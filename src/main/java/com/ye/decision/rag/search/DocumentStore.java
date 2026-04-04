package com.ye.decision.rag.search;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档向量存储抽象接口。
 * <p>
 * 负责文档的写入（含 Embedding 生成）和删除。
 * 实现类可对接 Milvus、Qdrant、Weaviate 等不同向量数据库。
 *
 * @author ye
 */
public interface DocumentStore {

    /**
     * 将文档片段写入向量库。
     * <p>
     * 实现类应负责：生成稠密向量 Embedding → 分批写入 → 返回。
     * 文档的 metadata 中应至少包含 kb_code、doc_id。
     *
     * @param documents 待写入的文档片段列表
     */
    void add(List<Document> documents);

    /**
     * 按过滤表达式删除向量。
     *
     * @param filterExpression Milvus 过滤表达式，例如 {@code doc_id == 'xxx'}
     */
    void delete(String filterExpression);
}
