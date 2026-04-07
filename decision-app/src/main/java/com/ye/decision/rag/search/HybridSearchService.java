package com.ye.decision.rag.search;

import java.util.List;

/**
 * 混合检索抽象接口。
 * <p>
 * 支持稠密向量 + 稀疏向量（BM25）混合检索，结果经 RRF 等策略融合排序。
 * 实现类可对接不同向量数据库的原生混合检索能力。
 *
 * @author ye
 */
public interface HybridSearchService {

    /**
     * 执行混合检索。
     *
     * @param query  用户查询文本
     * @param kbCode 知识库编码（用于过滤）
     * @param topK   返回结果数量
     * @return 经 RRF 融合排序并去重后的检索结果
     */
    List<SearchResult> search(String query, String kbCode, int topK);
}
