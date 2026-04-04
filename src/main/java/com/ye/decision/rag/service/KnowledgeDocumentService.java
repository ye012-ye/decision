package com.ye.decision.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.decision.rag.config.RagProperties;
import com.ye.decision.rag.domain.dto.DocumentUploadResp;
import com.ye.decision.rag.domain.dto.KnowledgeDocumentVO;
import com.ye.decision.rag.domain.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.domain.enums.DocumentStatus;
import com.ye.decision.rag.exception.RagErrorCode;
import com.ye.decision.rag.exception.RagException;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import com.ye.decision.rag.mq.DocumentIngestionPublisher;
import com.ye.decision.rag.search.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库文档管理服务。
 *
 * @author ye
 */
@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
        "pdf", "doc", "docx", "txt", "md", "html", "htm", "csv", "xls", "xlsx", "pptx"
    );

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseService kbService;
    private final DocumentIngestionPublisher ingestionPublisher;
    private final DocumentStore documentStore;
    private final RagProperties ragProperties;

    public KnowledgeDocumentService(KnowledgeDocumentMapper documentMapper,
                                    KnowledgeBaseService kbService,
                                    DocumentIngestionPublisher ingestionPublisher,
                                    DocumentStore documentStore,
                                    RagProperties ragProperties) {
        this.documentMapper = documentMapper;
        this.kbService = kbService;
        this.ingestionPublisher = ingestionPublisher;
        this.documentStore = documentStore;
        this.ragProperties = ragProperties;
    }

    public DocumentUploadResp upload(String kbCode, MultipartFile file, String uploadedBy) throws IOException {
        kbService.requireByCode(kbCode);

        if (file.isEmpty()) {
            throw new RagException(RagErrorCode.DOC_FILE_EMPTY);
        }

        // 文件大小校验
        if (file.getSize() > ragProperties.getMaxFileSize()) {
            throw new RagException(RagErrorCode.DOC_FILE_TOO_LARGE,
                "文件大小 " + (file.getSize() / 1024 / 1024) + "MB，上限 " + (ragProperties.getMaxFileSize() / 1024 / 1024) + "MB");
        }

        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new RagException(RagErrorCode.DOC_FILE_TYPE_UNSUPPORTED,
                fileType + "，支持: " + String.join(", ", ALLOWED_FILE_TYPES));
        }

        String docId = UUID.randomUUID().toString();

        Path dir = Paths.get(ragProperties.getUploadDir(), kbCode);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(docId + "." + fileType);
        file.transferTo(filePath.toFile());

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

        ingestionPublisher.publish(kbCode, docId, filePath.toString(), originalName);

        log.info("Document uploaded: kbCode={}, docId={}, fileName={}", kbCode, docId, originalName);
        return new DocumentUploadResp(docId, originalName, DocumentStatus.PENDING.getCode());
    }

    /**
     * 分页查询指定知识库下的文档列表。
     */
    public Page<KnowledgeDocumentVO> listByKbCode(String kbCode, int pageNum, int pageSize) {
        Page<KnowledgeDocumentEntity> page = new Page<>(pageNum, pageSize);
        Page<KnowledgeDocumentEntity> result = documentMapper.selectPage(page,
            new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbCode, kbCode)
                .orderByDesc(KnowledgeDocumentEntity::getCreatedAt));
        return (Page<KnowledgeDocumentVO>) result.convert(KnowledgeDocumentVO::from);
    }

    public KnowledgeDocumentEntity getByDocId(String docId) {
        return documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId) {
        KnowledgeDocumentEntity entity = getByDocId(docId);
        if (entity == null) {
            throw new RagException(RagErrorCode.DOC_NOT_FOUND, docId);
        }

        if (!docId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new RagException(RagErrorCode.DOC_ID_INVALID, docId);
        }

        documentStore.delete("doc_id == '" + docId + "'");

        try {
            Files.deleteIfExists(Paths.get(entity.getFilePath()));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", entity.getFilePath(), e);
        }

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
