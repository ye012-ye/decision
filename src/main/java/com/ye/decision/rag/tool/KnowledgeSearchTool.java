package com.ye.decision.rag.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.rag.dto.KnowledgeSearchReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * RAG 知识库检索工具。
 * Agent 在 ReAct 循环中可调用此工具，根据用户问题从 Milvus 中检索相关文档片段。
 */
public class KnowledgeSearchTool implements Function<KnowledgeSearchReq, String> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchTool(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(KnowledgeSearchReq req) {
        try {
            String kbCode = req.kbCode();
            int topK = req.topK() > 0 ? req.topK() : DEFAULT_TOP_K;

            SearchRequest searchRequest = SearchRequest.builder()
                .query(req.query())
                .topK(topK)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression("kb_code == '" + kbCode + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            if (results == null || results.isEmpty()) {
                return objectMapper.writeValueAsString(
                    Map.of("found", false, "message", "未在知识库 [" + kbCode + "] 中找到相关内容"));
            }

            List<Map<String, Object>> items = results.stream().map(doc -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", doc.getText());
                item.put("metadata", doc.getMetadata());
                return item;
            }).toList();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("found", true);
            resp.put("count", items.size());
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
