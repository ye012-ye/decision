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

import java.io.IOException;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档摄入管线服务。
 * <p>
 * 完整流程：
 * <ol>
 *   <li>Tika 解析文档（支持 PDF、Word、HTML 等多格式）</li>
 *   <li>注入元数据（{@code kb_code}、{@code doc_id}）到每个文档片段</li>
 *   <li>TokenTextSplitter 按 token 粒度切片（800 token，350 overlap）</li>
 *   <li>VectorStore.add() 自动调用 EmbeddingModel 生成向量并写入 Milvus</li>
 *   <li>更新 DB 中的 chunk_count 和 status</li>
 * </ol>
 * <p>
 * 由 {@link com.ye.decision.rag.mq.DocumentIngestionConsumer} 异步调用，
 * 不直接暴露为 REST 接口。
 *
 * @author ye
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
     * <p>
     * 异常策略：
     * <ul>
     *   <li>永久性故障（文件为空、解析失败等）——直接标记 FAILED，不重试</li>
     *   <li>瞬时故障（Milvus 连接超时、网络抖动等）——抛出异常，由 RabbitMQ 自动重试</li>
     * </ul>
     *
     * @param kbCode   知识库编码
     * @param docId    文档 UUID
     * @param filePath 文件在磁盘上的路径
     * @throws RuntimeException 瞬时故障时抛出，触发 MQ 重试
     */
    public void ingest(String kbCode, String docId, String filePath) {
        updateStatus(docId, DocumentStatus.PROCESSING, null);

        // ── 阶段 1：文档解析与切片（永久性操作，失败直接标记 FAILED） ──
        List<Document> chunks;
        try {
            Resource resource = new FileSystemResource(filePath);
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> rawDocs = reader.get();

            if (rawDocs.isEmpty()) {
                log.warn("Document is empty after parsing: docId={}", docId);
                updateStatus(docId, DocumentStatus.FAILED, "文档解析结果为空");
                return;
            }

            for (Document doc : rawDocs) {
                doc.getMetadata().putAll(Map.of(
                    "kb_code", kbCode,
                    "doc_id", docId
                ));
            }

            chunks = textSplitter.apply(rawDocs);
        } catch (Exception e) {
            // 解析/切片失败属于永久性故障（文件损坏、格式不支持等），标记 FAILED 不重试
            log.error("Document parsing failed (permanent): docId={}", docId, e);
            updateStatus(docId, DocumentStatus.FAILED, truncateErrorMsg(e.getMessage()));
            return;
        }

        // ── 阶段 2：写入 Milvus（可能因网络/服务不可用导致瞬时故障） ──
        try {
            vectorStore.add(chunks);
        } catch (Exception e) {
            if (isRetriable(e)) {
                // 瞬时故障：抛出异常触发 RabbitMQ 重试，状态保持 PROCESSING
                log.warn("Milvus write failed (retriable), will retry: docId={}", docId, e);
                throw new RuntimeException("Retriable ingestion failure: " + e.getMessage(), e);
            }
            // 非瞬时故障：标记 FAILED
            log.error("Milvus write failed (permanent): docId={}", docId, e);
            updateStatus(docId, DocumentStatus.FAILED, truncateErrorMsg(e.getMessage()));
            return;
        }

        updateDocChunkCount(docId, chunks.size());
        updateStatus(docId, DocumentStatus.COMPLETED, null);
        log.info("Document ingested: kbCode={}, docId={}, chunks={}", kbCode, docId, chunks.size());
    }

    /**
     * 判断异常是否为瞬时故障（值得重试）。
     * <p>
     * 瞬时故障包括：连接异常、IO 异常、超时等，通常由 Milvus/网络临时不可用导致。
     */
    private boolean isRetriable(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException
                || cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.UnknownHostException
                || cause instanceof IOException) {
                return true;
            }
            // 常见的连接类异常关键词
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Connection refused")
                || msg.contains("timed out")
                || msg.contains("connect time")
                || msg.contains("Unavailable"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String truncateErrorMsg(String errorMsg) {
        if (errorMsg != null && errorMsg.length() > MAX_ERROR_MSG_LENGTH) {
            return errorMsg.substring(0, MAX_ERROR_MSG_LENGTH);
        }
        return errorMsg;
    }

    private void updateStatus(String docId, DocumentStatus status, String errorMessage) {
        documentMapper.update(null,
            new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId)
                .set(KnowledgeDocumentEntity::getStatus, status)
                .set(KnowledgeDocumentEntity::getUpdatedAt, LocalDateTime.now())
                .set(errorMessage != null, KnowledgeDocumentEntity::getErrorMessage, errorMessage));
    }

    /**
     * 将文档标记为失败状态，供消费者在重试耗尽时调用。
     */
    public void markFailed(String docId, String errorMessage) {
        updateStatus(docId, DocumentStatus.FAILED, truncateErrorMsg(errorMessage));
    }

    private void updateDocChunkCount(String docId, int count) {
        documentMapper.update(null,
            new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId)
                .set(KnowledgeDocumentEntity::getChunkCount, count));
    }
}
