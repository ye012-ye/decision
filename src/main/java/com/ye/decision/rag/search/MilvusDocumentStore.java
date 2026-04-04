package com.ye.decision.rag.search;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ye.decision.rag.config.RagProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Milvus SDK 的文档存储实现。
 * <p>
 * 职责：
 * <ul>
 *   <li>调用 {@link EmbeddingModel} 生成稠密向量</li>
 *   <li>分批写入 Milvus（防止大文档 OOM）</li>
 *   <li>BM25 稀疏向量由 Milvus 端 BM25 Function 自动生成，无需客户端处理</li>
 * </ul>
 *
 * @author ye
 */
@Component
public class MilvusDocumentStore implements DocumentStore {

    private static final Logger log = LoggerFactory.getLogger(MilvusDocumentStore.class);
    private static final Gson GSON = new Gson();

    private final MilvusClientV2 milvusClient;
    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;

    public MilvusDocumentStore(MilvusClientV2 milvusClient,
                               EmbeddingModel embeddingModel,
                               RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        String collectionName = ragProperties.getMilvus().getCollectionName();
        int batchSize = ragProperties.getBatchSize();

        for (int i = 0; i < documents.size(); i += batchSize) {
            List<Document> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            insertBatch(collectionName, batch);
            log.debug("Inserted batch {}/{}, size={}", i / batchSize + 1,
                (documents.size() + batchSize - 1) / batchSize, batch.size());
        }

        log.info("Inserted {} documents into collection '{}'", documents.size(), collectionName);
    }

    @Override
    public void delete(String filterExpression) {
        milvusClient.delete(DeleteReq.builder()
            .collectionName(ragProperties.getMilvus().getCollectionName())
            .filter(filterExpression)
            .build());
    }

    /**
     * 将一批文档转为 Milvus 行数据并插入。
     * <p>
     * 每行包含：id, content, dense_vector, kb_code, doc_id, file_name, chunk_index。
     * sparse_vector 由 Milvus BM25 Function 自动填充。
     */
    private void insertBatch(String collectionName, List<Document> batch) {
        // 批量生成稠密向量
        List<String> texts = batch.stream().map(Document::getText).toList();
        List<float[]> embeddings = texts.stream().map(embeddingModel::embed).toList();

        // 构建行数据
        List<JsonObject> rows = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            Document doc = batch.get(i);
            Map<String, Object> meta = doc.getMetadata();
            float[] vector = embeddings.get(i);

            JsonObject row = new JsonObject();
            row.addProperty("id", doc.getId());
            row.addProperty("content", doc.getText());
            row.add("dense_vector", GSON.toJsonTree(toFloatList(vector)));
            // sparse_vector 由 BM25 Function 自动生成，不需要手动写入
            row.addProperty("kb_code", getString(meta, "kb_code"));
            row.addProperty("doc_id", getString(meta, "doc_id"));
            row.addProperty("file_name", getString(meta, "file_name"));
            row.addProperty("chunk_index", getInt(meta, "chunk_index"));
            rows.add(row);
        }

        milvusClient.insert(InsertReq.builder()
            .collectionName(collectionName)
            .data(rows)
            .build());
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }

    private static String getString(Map<String, Object> meta, String key) {
        Object val = meta.get(key);
        return val != null ? val.toString() : "";
    }

    private static int getInt(Map<String, Object> meta, String key) {
        Object val = meta.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }
}
