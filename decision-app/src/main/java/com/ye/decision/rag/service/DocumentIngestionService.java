package com.ye.decision.rag.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ye.decision.rag.domain.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.domain.enums.DocumentStatus;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import com.ye.decision.rag.search.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档摄入管线服务。
 * <p>
 * 完整流程：
 * <ol>
 *   <li>Tika 解析文档（支持 PDF、Word、HTML 等多格式）</li>
 *   <li>注入元数据（kb_code, doc_id, file_name, chunk_index）到每个文档片段</li>
 *   <li>TokenTextSplitter 按 token 粒度切片</li>
 *   <li>DocumentStore.add() 生成稠密向量 + 批量写入 Milvus（BM25 稀疏向量由 Milvus 自动生成）</li>
 *   <li>更新 DB 中的 chunk_count 和 status</li>
 * </ol>
 *
 * @author ye
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int MAX_ERROR_MSG_LENGTH = 900;

    private final DocumentStore documentStore;
    private final KnowledgeDocumentMapper documentMapper;
    private final TokenTextSplitter textSplitter;

    public DocumentIngestionService(DocumentStore documentStore,
                                    KnowledgeDocumentMapper documentMapper,
                                    TokenTextSplitter textSplitter) {
        this.documentStore = documentStore;
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
     * @param fileName 原始文件名（注入到 chunk 元数据，支持来源溯源）
     * @throws RuntimeException 瞬时故障时抛出，触发 MQ 重试
     */
    @Transactional(rollbackFor = Exception.class)
    public void ingest(String kbCode, String docId, String filePath, String fileName) {
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

            // 注入基础元数据
            for (Document doc : rawDocs) {
                doc.getMetadata().putAll(Map.of(
                    "kb_code", kbCode,
                    "doc_id", docId,
                    "file_name", fileName != null ? fileName : ""
                ));
            }

            // 切片
            chunks = textSplitter.apply(rawDocs);

            // 注入 chunk_index（切片后才能确定序号）
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).getMetadata().put("chunk_index", i);
            }
        } catch (Exception e) {
            log.error("Document parsing failed (permanent): docId={}", docId, e);
            updateStatus(docId, DocumentStatus.FAILED, truncateErrorMsg(e.getMessage()));
            return;
        }

        // ── 阶段 2：写入 Milvus（可能因网络/服务不可用导致瞬时故障） ──
        try {
            documentStore.add(chunks);
        } catch (Exception e) {
            if (isRetriable(e)) {
                log.warn("Milvus write failed (retriable), will retry: docId={}", docId, e);
                throw new RuntimeException("Retriable ingestion failure: " + e.getMessage(), e);
            }
            log.error("Milvus write failed (permanent): docId={}", docId, e);
            updateStatus(docId, DocumentStatus.FAILED, truncateErrorMsg(e.getMessage()));
            return;
        }

        updateDocChunkCount(docId, chunks.size());
        updateStatus(docId, DocumentStatus.COMPLETED, null);
        log.info("Document ingested: kbCode={}, docId={}, chunks={}", kbCode, docId, chunks.size());
    }

    public void markFailed(String docId, String errorMessage) {
        updateStatus(docId, DocumentStatus.FAILED, truncateErrorMsg(errorMessage));
    }
    /**
     * 判断异常是否可重试。
     * <p>
     * 仅支持 IOException 子类，以及 ConnectException / SocketTimeoutException / UnknownHostException。
     *
     * @param e 异常
     * @return 是否可重试
     */
    private boolean isRetriable(Throwable e) {
        Throwable cause = e;
        // 使用 IdentityHashMap 防御 cause 链自引用/成环导致的死循环
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (cause != null && seen.add(cause)) {
            // ConnectException / SocketTimeoutException / UnknownHostException 均为 IOException 子类
            if (cause instanceof IOException) {
                return true;
            }
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

    private void updateDocChunkCount(String docId, int count) {
        documentMapper.update(null,
            new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocId, docId)
                .set(KnowledgeDocumentEntity::getChunkCount, count));
    }
}
