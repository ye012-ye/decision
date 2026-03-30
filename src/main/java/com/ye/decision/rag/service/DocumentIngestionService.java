package com.ye.decision.rag.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ye.decision.rag.domain.DocumentStatus;
import com.ye.decision.rag.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档摄入管线：解析 → 切片 → 嵌入 → 写入 Milvus。
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int MAX_ERROR_MSG_LENGTH = 900;

    private final VectorStore vectorStore;
    private final KnowledgeDocumentMapper documentMapper;
    private final TokenTextSplitter textSplitter;

    public DocumentIngestionService(VectorStore vectorStore,
                                    KnowledgeDocumentMapper documentMapper,
                                    TokenTextSplitter textSplitter) {
        this.vectorStore = vectorStore;
        this.documentMapper = documentMapper;
        this.textSplitter = textSplitter;
    }

    /**
     * 执行完整的文档摄入流程。
     *
     * @param kbCode   知识库编码
     * @param docId    文档 UUID
     * @param filePath 文件在磁盘上的路径
     */
    public void ingest(String kbCode, String docId, String filePath) {
        updateStatus(docId, DocumentStatus.PROCESSING, null);
        try {
            // 1. 用 Tika 解析文档
            Resource resource = new FileSystemResource(filePath);
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> rawDocs = reader.get();

            if (rawDocs.isEmpty()) {
                log.warn("Document is empty after parsing: docId={}", docId);
                updateStatus(docId, DocumentStatus.FAILED, "文档解析结果为空");
                return;
            }

            // 2. 为每个文档注入元数据
            for (Document doc : rawDocs) {
                doc.getMetadata().putAll(Map.of(
                    "kb_code", kbCode,
                    "doc_id", docId
                ));
            }

            // 3. 切片
            List<Document> chunks = textSplitter.apply(rawDocs);

            // 4. 嵌入 + 写入 Milvus（VectorStore.add 内部自动调用 EmbeddingModel）
            vectorStore.add(chunks);

            // 5. 更新文档状态
            updateDocChunkCount(docId, chunks.size());
            updateStatus(docId, DocumentStatus.COMPLETED, null);
            log.info("Document ingested: kbCode={}, docId={}, chunks={}", kbCode, docId, chunks.size());
        } catch (Exception e) {
            log.error("Document ingestion failed: docId={}", docId, e);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > MAX_ERROR_MSG_LENGTH) {
                errorMsg = errorMsg.substring(0, MAX_ERROR_MSG_LENGTH);
            }
            updateStatus(docId, DocumentStatus.FAILED, errorMsg);
        }
    }

    private void updateStatus(String docId, DocumentStatus status, String errorMessage) {
        documentMapper.update(null,
            new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId)
                .set(KnowledgeDocumentEntity::getStatus, status)
                .set(KnowledgeDocumentEntity::getUpdatedAt, LocalDateTime.now())
                .set(errorMessage != null, KnowledgeDocumentEntity::getErrorMessage, errorMessage));
    }

    private void updateDocChunkCount(String docId, int count) {
        documentMapper.update(null,
            new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId)
                .set(KnowledgeDocumentEntity::getChunkCount, count));
    }
}
