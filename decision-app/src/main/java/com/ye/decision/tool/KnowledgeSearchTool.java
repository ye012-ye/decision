package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.rag.search.HybridSearchService;
import com.ye.decision.rag.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库混合检索工具。
 * <p>
 * Agent 在 ReAct 循环中调用此工具，底层通过 {@link HybridSearchService}
 * 执行稠密向量 + BM25 稀疏向量混合检索，结果经 RRF 融合排序。
 *
 * @author ye
 */
@Component
public class KnowledgeSearchTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);
    private static final int DEFAULT_TOP_K = 5;

    private final HybridSearchService hybridSearchService;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchTool(HybridSearchService hybridSearchService, ObjectMapper objectMapper) {
        this.hybridSearchService = hybridSearchService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "knowledgeSearchTool", description = "在企业知识库中搜索相关文档。适用于查询产品文档、操作手册、FAQ、政策规范、技术文档等非结构化知识。需要指定知识库编码(kbCode)和查询内容(query)。")
    public String search(
            @ToolParam(description = "自然语言检索内容") String query,
            @ToolParam(description = "目标知识库编码，如 product-faq、policy-manual") String kbCode,
            @ToolParam(description = "返回最相似的文档片段数量，默认5", required = false) Integer topK) {
        try {
            int effectiveTopK = (topK != null && topK > 0) ? topK : DEFAULT_TOP_K;

            // 运行时校验 kbCode（来自 LLM 自动填充，不经过 @Valid）
            if (kbCode == null || !kbCode.matches("^[a-zA-Z0-9_-]+$")) {
                return objectMapper.writeValueAsString(
                    Map.of("error", "invalid_kb_code", "message", "知识库编码格式无效: " + kbCode));
            }

            List<SearchResult> results = hybridSearchService.search(query, kbCode, effectiveTopK);

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
            log.error("Knowledge search failed: kbCode={}, query={}", kbCode, query, e);
            try {
                return objectMapper.writeValueAsString(
                    Map.of("error", "rag_error", "message", e.getMessage() != null ? e.getMessage() : "unknown"));
            } catch (Exception ex) {
                return "{\"error\":\"rag_error\",\"message\":\"serialization_failed\"}";
            }
        }
    }
}
