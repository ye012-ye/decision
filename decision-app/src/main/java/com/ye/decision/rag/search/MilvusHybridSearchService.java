package com.ye.decision.rag.search;

import com.ye.decision.rag.config.RagProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于 Milvus 的混合检索实现。
 * <p>
 * 检索策略：
 * <ol>
 *   <li><b>Dense Search</b> — 用 EmbeddingModel 将 query 编码为稠密向量，在 dense_vector 字段做 ANN 检索</li>
 *   <li><b>Sparse Search (BM25)</b> — 将 query 原文传入 sparse_vector 字段，Milvus 端 BM25 Function 自动分词 + 匹配</li>
 *   <li><b>RRF Fusion</b> — 用 Reciprocal Rank Fusion 融合两路排序，综合语义相关性和关键词匹配</li>
 *   <li><b>Deduplication</b> — 基于词级 Jaccard 系数过滤因 overlap 切片导致的重复片段</li>
 * </ol>
 *
 * @author ye
 */
@Component
public class MilvusHybridSearchService implements HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(MilvusHybridSearchService.class);

    /** 多取系数，为去重留出余量 */
    private static final int OVER_FETCH_FACTOR = 2;

    /** 文本重叠率阈值，超过此值视为冗余 */
    private static final double OVERLAP_THRESHOLD = 0.5;

    /** 检索返回的输出字段 */
    private static final List<String> OUTPUT_FIELDS = List.of(
        "content", "kb_code", "doc_id", "file_name", "chunk_index"
    );

    private final MilvusClientV2 milvusClient;
    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;

    public MilvusHybridSearchService(MilvusClientV2 milvusClient,
                                     EmbeddingModel embeddingModel,
                                     RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
    }

    @Override
    public List<SearchResult> search(String query, String kbCode, int topK) {
        String collectionName = ragProperties.getMilvus().getCollectionName();
        int fetchK = topK * OVER_FETCH_FACTOR;

        // ── 1. Dense ANN Search（语义检索） ──────────────────────
        String filter = "kb_code == '" + kbCode + "'";
        float[] denseEmbedding = embeddingModel.embed(query);
        AnnSearchReq denseReq = AnnSearchReq.builder()
            .vectorFieldName("dense_vector")
            .vectors(Collections.singletonList(new FloatVec(denseEmbedding)))
            //limit: 搜索结果数量，不能超过索引的 topK
            .limit(fetchK)
            .metricType(IndexParam.MetricType.COSINE)
            .filter(filter)
            .build();

        // ── 2. Sparse BM25 Search（关键词检索） ──────────────────
        AnnSearchReq sparseReq = AnnSearchReq.builder()
            .vectorFieldName("sparse_vector")
            .vectors(Collections.singletonList(new EmbeddedText(query)))
            .limit(fetchK)
            .metricType(IndexParam.MetricType.BM25)
            .filter(filter)
            .build();

        // ── 3. Hybrid Search + RRF 融合排序 ─────────────────────
        int rrfK = ragProperties.getMilvus().getRrfK();

        HybridSearchReq hybridReq = HybridSearchReq.builder()
            .collectionName(collectionName)
            .searchRequests(List.of(denseReq, sparseReq))
            .ranker(RRFRanker.builder().k(rrfK).build())
            .limit(fetchK)
            .outFields(OUTPUT_FIELDS)
            .build();

        SearchResp resp = milvusClient.hybridSearch(hybridReq);

        // ── 4. 解析结果 ─────────────────────────────────────────
        List<SearchResult> rawResults = parseResults(resp);

        // ── 5. 去重 ─────────────────────────────────────────────
        List<SearchResult> deduplicated = deduplicateByOverlap(rawResults, topK);

        log.debug("Hybrid search: query='{}', kbCode='{}', raw={}, dedup={}",
            query, kbCode, rawResults.size(), deduplicated.size());

        return deduplicated;
    }

    /**
     * 解析 Milvus HybridSearch 响应为 SearchResult 列表。
     */
    private List<SearchResult> parseResults(SearchResp resp) {
        List<SearchResult> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = resp.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return results;
        }

        for (SearchResp.SearchResult hit : searchResults.get(0)) {
            Map<String, Object> entity = hit.getEntity();
            String content = entity.getOrDefault("content", "").toString();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("kb_code", entity.get("kb_code"));
            metadata.put("doc_id", entity.get("doc_id"));
            metadata.put("file_name", entity.get("file_name"));
            metadata.put("chunk_index", entity.get("chunk_index"));

            results.add(new SearchResult(content, hit.getScore(), metadata));
        }
        return results;
    }

    /**
     * 基于词级 Jaccard 系数去重，过滤因 overlap 切片产生的高度重叠片段。
     */
    private List<SearchResult> deduplicateByOverlap(List<SearchResult> results, int topK) {
        List<SearchResult> selected = new ArrayList<>();
        for (SearchResult candidate : results) {
            if (selected.size() >= topK) {
                break;
            }
            if (candidate.content() == null || candidate.content().isBlank()) {
                continue;
            }
            boolean redundant = selected.stream().anyMatch(accepted ->
                computeOverlapRatio(accepted.content(), candidate.content()) > OVERLAP_THRESHOLD);
            if (!redundant) {
                selected.add(candidate);
            }
        }
        return selected;
    }
    /**
    * 计算两个文本的词级 Jaccard 系数。
     * 越高的值，表示越相似。
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
