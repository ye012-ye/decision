package com.ye.decision.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.rag.tool.KnowledgeSearchTool;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    public static final String INGESTION_EXCHANGE     = "doc.ingestion.exchange";
    public static final String INGESTION_QUEUE        = "doc.ingestion.queue";
    public static final String INGESTION_ROUTING      = "doc.ingestion";
    public static final String INGESTION_DLX_EXCHANGE = "doc.ingestion.dlx.exchange";
    public static final String INGESTION_DLQ          = "doc.ingestion.dlq";
    public static final String INGESTION_DLQ_ROUTING  = "doc.ingestion.dlq";

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(800, 350, 5, 10000, true);
    }

    @Bean
    public KnowledgeSearchTool knowledgeSearchTool(VectorStore vectorStore, ObjectMapper objectMapper) {
        return new KnowledgeSearchTool(vectorStore, objectMapper);
    }

    // ── 文档摄入主队列（带死信路由） ──────────────────────────

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

    // ── 死信队列（失败消息可追溯 / 人工重试） ─────────────────

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
