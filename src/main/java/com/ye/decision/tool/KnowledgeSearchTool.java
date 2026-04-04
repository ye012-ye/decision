package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.rag.domain.dto.KnowledgeSearchReq;
import com.ye.decision.rag.search.HybridSearchService;
import com.ye.decision.rag.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * RAG 知识库混合检索工具。
 * <p>
 * Agent 在 ReAct 循环中调用此工具，底层通过 {@link HybridSearchService}
 * 执行稠密向量 + BM25 稀疏向量混合检索，结果经 RRF 融合排序。
 *
 * @author ye
 */
public class KnowledgeSearchTool implements Function<KnowledgeSearchReq, String> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);
    private static final int DEFAULT_TOP_K = 5;

    private final HybridSearchService hybridSearchService;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchTool(HybridSearchService hybridSearchService, ObjectMapper objectMapper) {
        this.hybridSearchService = hybridSearchService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(KnowledgeSearchReq req) {
        try {
            String kbCode = req.kbCode();
            int topK = req.topK() > 0 ? req.topK() : DEFAULT_TOP_K;

            // 运行时校验 kbCode（来自 LLM 自动填充，不经过 @Valid）
            if (kbCode == null || !kbCode.matches("^[a-zA-Z0-9_-]+$")) {
                return objectMapper.writeValueAsString(
                    Map.of("error", "invalid_kb_code", "message", "知识库编码格式无效: " + kbCode));
            }

            List<SearchResult> results = hybridSearchService.search(req.query(), kbCode, topK);

            if (results.isEmpty()) {
                return objectMapper.writeValueAsString(
                    Map.of("found", false, "message", "未在知识库 [" + kbCode + "] 中找到相关内容"));
            }

            List<Map<String, Object>> items = results.stream().map(r -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", r.content());
                item.put("score", r.score());
                item.put("source", r.metadata().getOrDefault("file_name", ""));
                item.put("metadata", r.metadata());
                return item;
            }).toList();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("found", true);
            resp.put("count", items.size());
            resp.put("search_mode", "hybrid (dense + bm25 sparse) + RRF");
            resp.put("results", items);
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            log.error("Knowledge search failed: kbCode={}, query={}", req.kbCode(), req.query(), e);
            try {
                return objectMapper.writeValueAsString(
                    Map.of("error", "rag_error", "message", e.getMessage() != null ? e.getMessage() : "unknown"));
            } catch (Exception ex) {
                return "{\"error\":\"rag_error\",\"message\":\"serialization_failed\"}";
            }
        }
    }
}
