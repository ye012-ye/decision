package com.ye.decision.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.tool.KnowledgeSearchTool;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块核心配置。
 * <p>
 * 职责：
 * <ul>
 *   <li>注册文本切片器 {@link TokenTextSplitter}，用于将文档拆分为适合嵌入的语义片段</li>
 *   <li>注册知识库检索工具 {@link KnowledgeSearchTool}，供 Agent ReAct 循环调用</li>
 *   <li>声明文档摄入主队列及死信队列（DLQ），实现异步摄入与失败消息可追溯</li>
 * </ul>
 *
 * <h3>MQ 拓扑</h3>
 * <pre>
 * Producer → [doc.ingestion.exchange] → [doc.ingestion.queue] → Consumer
 *                                              ↓ (reject/nack)
 *                                      [doc.ingestion.dlx.exchange] → [doc.ingestion.dlq]
 * </pre>
 *
 * @author ye
 */
@Configuration
public class RagConfig {

    /* ── 主队列常量 ─────────────────────────────────────────── */

    public static final String INGESTION_EXCHANGE     = "doc.ingestion.exchange";
    public static final String INGESTION_QUEUE        = "doc.ingestion.queue";
    public static final String INGESTION_ROUTING      = "doc.ingestion";

    /* ── 死信队列常量 ───────────────────────────────────────── */

    public static final String INGESTION_DLX_EXCHANGE = "doc.ingestion.dlx.exchange";
    public static final String INGESTION_DLQ          = "doc.ingestion.dlq";
    public static final String INGESTION_DLQ_ROUTING  = "doc.ingestion.dlq";

    // ── Bean 声明 ─────────────────────────────────────────────

    /**
     * 文本切片器：将长文档按 token 粒度拆分为重叠片段。
     * <p>参数：maxTokens=800, overlapTokens=350, minChunkSizeChars=5,
     * maxNumChunks=10000, keepSeparator=true
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(800, 350, 5, 10000, true);
    }

    /**
     * 知识库向量检索工具，注册为 Spring Bean 后由 {@link com.ye.decision.config.AiConfig}
     * 包装为 Agent ToolCallback。
     */
    @Bean
    public KnowledgeSearchTool knowledgeSearchTool(VectorStore vectorStore, ObjectMapper objectMapper) {
        return new KnowledgeSearchTool(vectorStore, objectMapper);
    }

    // ── 文档摄入主队列（带死信路由） ──────────────────────────

    @Bean
    public DirectExchange docIngestionExchange() {
        return ExchangeBuilder.directExchange(INGESTION_EXCHANGE).durable(true).build();
    }

    /** 主队列：消费失败的消息将自动路由到死信队列。 */
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

    // ── 死信队列（失败消息可追溯 / 人工重试） ─────────────────

    @Bean
    public DirectExchange docIngestionDlxExchange() {
        return ExchangeBuilder.directExchange(INGESTION_DLX_EXCHANGE).durable(true).build();
    }

    /** 死信队列：存放摄入失败的消息，可通过管理后台查看或重新投递。 */
    @Bean
    public Queue docIngestionDlq() {
        return QueueBuilder.durable(INGESTION_DLQ).build();
    }

    @Bean
    public Binding docIngestionDlqBinding(DirectExchange docIngestionDlxExchange, Queue docIngestionDlq) {
        return BindingBuilder.bind(docIngestionDlq).to(docIngestionDlxExchange).with(INGESTION_DLQ_ROUTING);
    }
}
