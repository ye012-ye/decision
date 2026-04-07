package com.ye.decision.rag.search;

import com.ye.decision.rag.config.RagProperties;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 应用启动时自动初始化 Milvus Collection。
 * <p>
 * Schema 设计（支持混合检索）：
 * <ul>
 *   <li>{@code id}            — VARCHAR(36), 主键</li>
 *   <li>{@code content}       — VARCHAR(65535), 文本内容，开启中文分析器供 BM25 使用</li>
 *   <li>{@code dense_vector}  — FLOAT_VECTOR, 稠密向量（EmbeddingModel 生成）</li>
 *   <li>{@code sparse_vector} — SPARSE_FLOAT_VECTOR, 稀疏向量（BM25 Function 自动生成）</li>
 *   <li>{@code kb_code}       — VARCHAR(64), 知识库编码</li>
 *   <li>{@code doc_id}        — VARCHAR(36), 文档 ID</li>
 *   <li>{@code file_name}     — VARCHAR(256), 原始文件名</li>
 *   <li>{@code chunk_index}   — INT32, 分片序号</li>
 * </ul>
 *
 * @author ye
 */
@Component
public class MilvusCollectionInitializer {

    private static final Logger log = LoggerFactory.getLogger(MilvusCollectionInitializer.class);

    private final MilvusClientV2 milvusClient;
    private final RagProperties ragProperties;

    public MilvusCollectionInitializer(MilvusClientV2 milvusClient, RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.ragProperties = ragProperties;
    }

    /**
     * 应用启动时自动初始化 Milvus Collection。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        String collectionName = ragProperties.getMilvus().getCollectionName();

        boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
            .collectionName(collectionName).build());
        if (exists) {
            log.info("Milvus collection '{}' already exists, skip initialization", collectionName);
            return;
        }

        log.info("Creating Milvus collection '{}' with hybrid search schema (Dense + BM25 Sparse)", collectionName);

        // ── 构建 Schema ──────────────────────────────────────────
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();

        // 主键
        schema.addField(AddFieldReq.builder()
            .fieldName("id").dataType(DataType.VarChar).isPrimaryKey(true).maxLength(36)
            .build());

        // 文本内容（开启中文分析器，供 BM25 Function 使用）
        schema.addField(AddFieldReq.builder()
            .fieldName("content").dataType(DataType.VarChar).maxLength(65535)
            .enableAnalyzer(true)
            .analyzerParams(Map.of("type", "chinese"))
            .build());

        // 稠密向量（EmbeddingModel 生成）
        schema.addField(AddFieldReq.builder()
            .fieldName("dense_vector").dataType(DataType.FloatVector)
            .dimension(ragProperties.getMilvus().getDenseDimension())
            .build());

        // 稀疏向量（BM25 Function 自动填充，无需手动写入）
        schema.addField(AddFieldReq.builder()
            .fieldName("sparse_vector").dataType(DataType.SparseFloatVector)
            .build());

        // 元数据字段
        schema.addField(AddFieldReq.builder()
            .fieldName("kb_code").dataType(DataType.VarChar).maxLength(64)
            .build());
        schema.addField(AddFieldReq.builder()
            .fieldName("doc_id").dataType(DataType.VarChar).maxLength(36)
            .build());
        schema.addField(AddFieldReq.builder()
            .fieldName("file_name").dataType(DataType.VarChar).maxLength(256)
            .build());
        schema.addField(AddFieldReq.builder()
            .fieldName("chunk_index").dataType(DataType.Int32)
            .build());

        // BM25 Function：content → sparse_vector
        schema.addFunction(CreateCollectionReq.Function.builder()
            .functionType(FunctionType.BM25)
            .name("bm25_func")
            .inputFieldNames(Collections.singletonList("content"))
            .outputFieldNames(Collections.singletonList("sparse_vector"))
            .build());

        // ── 索引 ──────────────────────────────────────────────────
        List<IndexParam> indexes = new ArrayList<>();
        indexes.add(IndexParam.builder()
            .fieldName("dense_vector")
            .indexType(IndexParam.IndexType.AUTOINDEX)
            .metricType(IndexParam.MetricType.COSINE)
            .build());
        indexes.add(IndexParam.builder()
            .fieldName("sparse_vector")
            .indexType(IndexParam.IndexType.AUTOINDEX)
            .metricType(IndexParam.MetricType.BM25)
            .build());

        // ── 创建 Collection ───────────────────────────────────────
        milvusClient.createCollection(CreateCollectionReq.builder()
            .collectionName(collectionName)
            .collectionSchema(schema)
            .indexParams(indexes)
            .build());

        log.info("Milvus collection '{}' created successfully", collectionName);
    }
}
