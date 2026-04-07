package com.ye.decision.rag.search;

import java.util.Map;

/**
 * 混合检索结果条目。
 *
 * @author ye
 * @param content  文档片段文本
 * @param score    RRF 融合后的综合得分
 * @param metadata 元数据（kb_code, doc_id, file_name, chunk_index 等）
 */
public record SearchResult(
    String content,
    double score,
    Map<String, Object> metadata
) {}
