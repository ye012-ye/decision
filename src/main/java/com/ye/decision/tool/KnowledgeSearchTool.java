package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.rag.dto.KnowledgeSearchReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;
import java.util.function.Function;

/**
 * RAG 知识库检索工具。
 * <p>
 * Agent 在 ReAct 循环中可调用此工具，根据用户问题从 Milvus 中检索相关文档片段。
 * 检索后自动对结果去重，过滤掉因 overlap 切片产生的高度重叠片段。
 *
 * @author ye
 */
public class KnowledgeSearchTool implements Function<KnowledgeSearchReq, String> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    private static final int DEFAULT_TOP_K = 5;

    /**
     * 多取系数：实际向 Milvus 请求 topK * OVER_FETCH_FACTOR 条结果，
     * 去重后截取前 topK 条，确保去重后仍有足够的独立内容。
     */
    private static final int OVER_FETCH_FACTOR = 2;

    /**
     * 文本重叠率阈值：若候选片段与已选片段的重叠率超过此值，则视为冗余并跳过。
     */
    private static final double OVERLAP_THRESHOLD = 0.5;

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

            // 多取一些结果，为去重留出余量
            SearchRequest searchRequest = SearchRequest.builder()
                .query(req.query())
                .topK(topK * OVER_FETCH_FACTOR)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression("kb_code == '" + kbCode + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            if (results == null || results.isEmpty()) {
                return objectMapper.writeValueAsString(
                    Map.of("found", false, "message", "未在知识库 [" + kbCode + "] 中找到相关内容"));
            }

            // 去重：过滤掉与已选片段文本高度重叠的结果
            List<Document> deduplicated = deduplicateByOverlap(results, topK);

            List<Map<String, Object>> items = deduplicated.stream().map(doc -> {
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

    /**
     * 基于文本重叠率对检索结果去重。
     * <p>
     * 由于 TokenTextSplitter 的 overlap 机制，相邻 chunk 会共享大段文本。
     * 按相似度排序（Milvus 返回顺序）逐条判断：如果候选片段与已选中的任一片段
     * 文本重叠率超过 {@link #OVERLAP_THRESHOLD}，则跳过该片段。
     *
     * @param results Milvus 返回的原始结果（已按相似度降序）
     * @param topK    最终需要的结果数量
     * @return 去重后的结果列表，最多 topK 条
     */
    private List<Document> deduplicateByOverlap(List<Document> results, int topK) {
        List<Document> selected = new ArrayList<>();
        for (Document candidate : results) {
            if (selected.size() >= topK) {
                break;
            }
            String candidateText = candidate.getText();
            if (candidateText == null || candidateText.isBlank()) {
                continue;
            }
            boolean redundant = selected.stream().anyMatch(accepted ->
                computeOverlapRatio(accepted.getText(), candidateText) > OVERLAP_THRESHOLD);
            if (!redundant) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    /**
     * 计算两段文本基于词集合的重叠率（Jaccard 系数）。
     * <p>
     * 使用空白字符分词，计算交集大小 / 较短文本的词数。
     * 这种方式能有效检测因 overlap 切片产生的大段共享文本。
     *
     * @return 重叠率 [0.0, 1.0]
     */
    private double computeOverlapRatio(String textA, String textB) {
        Set<String> tokensA = new HashSet<>(Arrays.asList(textA.split("\\s+")));
        Set<String> tokensB = new HashSet<>(Arrays.asList(textB.split("\\s+")));
        int minSize = Math.min(tokensA.size(), tokensB.size());
        if (minSize == 0) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        return (double) intersection.size() / minSize;
    }
}
