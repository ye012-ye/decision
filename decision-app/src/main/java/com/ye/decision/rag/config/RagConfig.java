package com.ye.decision.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.rag.search.HybridSearchService;
import com.ye.decision.tool.KnowledgeSearchTool;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块核心配置。
 * <p>
 * 职责：
 * <ul>
 *   <li>创建 {@link MilvusClientV2} 连接（直接使用 Milvus SDK，支持混合检索）</li>
 *   <li>注册文本切片器 {@link TokenTextSplitter}（参数由 {@link RagProperties} 驱动）</li>
 *   <li>注册知识库检索工具 {@link KnowledgeSearchTool}</li>
 *   <li>声明文档摄入主队列及死信队列</li>
 * </ul>
 *
 * @author ye
 */
@Configuration
public class RagConfig {

    /* ── MQ 常量 ───────────────────────────────────────────────── */

    public static final String INGESTION_EXCHANGE     = "doc.ingestion.exchange";
    public static final String INGESTION_QUEUE        = "doc.ingestion.queue";
    public static final String INGESTION_ROUTING      = "doc.ingestion";
    public static final String INGESTION_DLX_EXCHANGE = "doc.ingestion.dlx.exchange";
    public static final String INGESTION_DLQ          = "doc.ingestion.dlq";
    public static final String INGESTION_DLQ_ROUTING  = "doc.ingestion.dlq";

    // ── Milvus Client ─────────────────────────────────────────────

    @Bean
    public MilvusClientV2 milvusClient(RagProperties ragProperties) {
        ConnectConfig config = ConnectConfig.builder()
            .uri(ragProperties.getMilvus().getUri())
            .build();
        return new MilvusClientV2(config);
    }

    // ── 文本切片器（参数可配置化） ────────────────────────────────

    @Bean
    public TokenTextSplitter tokenTextSplitter(RagProperties ragProperties) {
        return new TokenTextSplitter(
            ragProperties.getChunkSize(),
            ragProperties.getChunkOverlap(),
            5,      // minChunkSizeChars
            10000,  // maxNumChunks
            true    // keepSeparator
        );
    }

    // ── 知识库检索工具 ────────────────────────────────────────────

    @Bean
    public KnowledgeSearchTool knowledgeSearchTool(HybridSearchService hybridSearchService,
                                                   ObjectMapper objectMapper) {
        return new KnowledgeSearchTool(hybridSearchService, objectMapper);
    }

    // ── 文档摄入主队列（带死信路由） ──────────────────────────────

    @Bean
    public DirectExchange docIngestionExchange() {
        return ExchangeBuilder.directExchange(INGESTION_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue docIngestionQueue() {
        return QueueBuilder.durable(INGESTION_QUEUE)
            .deadLetterExchange(INGESTION_DLX_EXCHANGE)
            .deadLetterRoutingKey(INGESTION_DLQ_ROUTING)
            .build();
    }

    @Bean
    public Binding docIngestionBinding(DirectExchange docIngestionExchange, Queue docIngestionQueue) {
        return BindingBuilder.bind(docIngestionQueue).to(docIngestionExchange).with(INGESTION_ROUTING);
    }

    // ── 死信队列 ──────────────────────────────────────────────────

    @Bean
    public DirectExchange docIngestionDlxExchange() {
        return ExchangeBuilder.directExchange(INGESTION_DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue docIngestionDlq() {
        return QueueBuilder.durable(INGESTION_DLQ).build();
    }

    @Bean
    public Binding docIngestionDlqBinding(DirectExchange docIngestionDlxExchange, Queue docIngestionDlq) {
        return BindingBuilder.bind(docIngestionDlq).to(docIngestionDlxExchange).with(INGESTION_DLQ_ROUTING);
    }
}
