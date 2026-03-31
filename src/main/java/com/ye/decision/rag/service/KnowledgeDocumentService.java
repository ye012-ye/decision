package com.ye.decision.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.decision.rag.domain.DocumentStatus;
import com.ye.decision.rag.dto.DocumentUploadResp;
import com.ye.decision.rag.dto.KnowledgeDocumentVO;
import com.ye.decision.rag.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.exception.RagErrorCode;
import com.ye.decision.rag.exception.RagException;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import com.ye.decision.rag.mq.DocumentIngestionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库文档管理服务。
 * <p>
 * 负责文档的上传（含类型白名单校验）、状态查询和删除。
 * 上传后通过 MQ 异步触发摄入管线，前端可轮询状态接口跟踪进度。
 *
 * @author ye
 * @see DocumentIngestionService
 */
@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    /** 允许上传的文件类型白名单 */
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
        "pdf", "doc", "docx", "txt", "md", "html", "htm", "csv", "xls", "xlsx", "pptx"
    );

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseService kbService;
    private final DocumentIngestionPublisher ingestionPublisher;
    private final VectorStore vectorStore;

    @Value("${decision.rag.upload-dir:./uploads/knowledge}")
    private String uploadDir;

    public KnowledgeDocumentService(KnowledgeDocumentMapper documentMapper,
                                    KnowledgeBaseService kbService,
                                    DocumentIngestionPublisher ingestionPublisher,
                                    VectorStore vectorStore) {
        this.documentMapper = documentMapper;
        this.kbService = kbService;
        this.ingestionPublisher = ingestionPublisher;
        this.vectorStore = vectorStore;
    }

    /**
     * 上传文档到指定知识库。
     * <p>
     * 流程：校验知识库存在 → 校验文件非空及类型 → 落盘 → 写 DB(PENDING) → 发 MQ 异步摄入。
     *
     * @param kbCode     目标知识库编码
     * @param file       上传的文件
     * @param uploadedBy 上传者标识（可选）
     * @return 包含 docId 和初始状态的响应
     */
    public DocumentUploadResp upload(String kbCode, MultipartFile file, String uploadedBy) throws IOException {
        // 1. 校验知识库存在
        kbService.requireByCode(kbCode);

        // 2. 校验文件非空
        if (file.isEmpty()) {
            throw new RagException(RagErrorCode.DOC_FILE_EMPTY);
        }

        // 3. 校验文件类型
        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new RagException(RagErrorCode.DOC_FILE_TYPE_UNSUPPORTED,
                fileType + "，支持: " + String.join(", ", ALLOWED_FILE_TYPES));
        }

        String docId = UUID.randomUUID().toString();

        // 4. 保存文件到磁盘
        Path dir = Paths.get(uploadDir, kbCode);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(docId + "." + fileType);
        file.transferTo(filePath.toFile());

        // 5. 创建文档记录
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKbCode(kbCode);
        entity.setDocId(docId);
        entity.setFileName(originalName);
        entity.setFileType(fileType);
        entity.setFileSize(file.getSize());
        entity.setFilePath(filePath.toString());
        entity.setChunkCount(0);
        entity.setStatus(DocumentStatus.PENDING);
        entity.setUploadedBy(uploadedBy);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(entity);

        // 6. 发布异步摄入任务
        ingestionPublisher.publish(kbCode, docId, filePath.toString());

        log.info("Document uploaded: kbCode={}, docId={}, fileName={}", kbCode, docId, originalName);
        return new DocumentUploadResp(docId, originalName, DocumentStatus.PENDING.getCode());
    }

    public List<KnowledgeDocumentVO> listByKbCode(String kbCode) {
        return documentMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbCode, kbCode)
                .orderByDesc(KnowledgeDocumentEntity::getCreatedAt))
            .stream()
            .map(KnowledgeDocumentVO::from)
            .toList();
    }

    public KnowledgeDocumentEntity getByDocId(String docId) {
        return documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId));
    }

    /**
     * 删除文档及其全部关联数据（事务）。
     * <p>
     * 删除顺序：Milvus 向量 → 磁盘文件 → DB 记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId) {
        KnowledgeDocumentEntity entity = getByDocId(docId);
        if (entity == null) {
            throw new RagException(RagErrorCode.DOC_NOT_FOUND, docId);
        }

        // 校验 docId 格式
        if (!docId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new RagException(RagErrorCode.DOC_ID_INVALID, docId);
        }

        // 1. 删除 Milvus 中该文档的向量
        vectorStore.delete("doc_id == '" + docId + "'");

        // 2. 删除磁盘文件
        try {
            Files.deleteIfExists(Paths.get(entity.getFilePath()));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", entity.getFilePath(), e);
        }

        // 3. 删除 DB 记录
        documentMapper.deleteById(entity.getId());

        log.info("Document deleted: docId={}", docId);
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
