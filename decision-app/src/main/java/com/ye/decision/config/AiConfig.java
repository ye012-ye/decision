package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.ApiCallReq;
import com.ye.decision.domain.dto.QueryMysqlReq;
import com.ye.decision.domain.dto.QueryRedisReq;
import com.ye.decision.mq.ChatMemoryPublisher;
import com.ye.decision.rag.domain.dto.KnowledgeSearchReq;
import com.ye.decision.tool.KnowledgeSearchTool;
import com.ye.decision.tool.CallExternalApiTool;
import com.ye.decision.tool.QueryMysqlTool;
import com.ye.decision.tool.QueryRedisTool;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ye
 */
@Configuration
public class AiConfig {

    @Value("${decision.agent.memory-window-size:10}")
    private int memoryWindowSize;

    @Bean
    public ChatMemory chatMemory(RedissonClient redissonClient,
                                 ObjectMapper objectMapper,
                                 ChatMemoryPublisher publisher) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new RedissonChatMemoryRepository(redissonClient, objectMapper, publisher))
            .maxMessages(memoryWindowSize)
            .build();
    }

    // MCP Client 自动注入的 ToolCallbackProvider（来自 MCP Server 的工具）
    @Autowired(required = false)
    private ToolCallbackProvider mcpToolCallbackProvider;

    @Bean
    public List<ToolCallback> toolCallbacks(QueryMysqlTool queryMysqlTool,
                                            QueryRedisTool queryRedisTool,
                                            CallExternalApiTool callExternalApiTool,
                                            KnowledgeSearchTool knowledgeSearchTool) {
        List<ToolCallback> callbacks = new ArrayList<>(List.of(
            FunctionToolCallback.builder("queryMysqlTool", queryMysqlTool)
                .description("查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。")
                .inputType(QueryMysqlReq.class)
                .build(),
            FunctionToolCallback.builder("queryRedisTool", queryRedisTool)
                .description("查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。")
                .inputType(QueryRedisReq.class)
                .build(),
            FunctionToolCallback.builder("callExternalApiTool", callExternalApiTool)
                .description("调用外部第三方服务，包括天气查询（weather）、物流追踪（logistics）、汇率查询（exchange-rate）。")
                .inputType(ApiCallReq.class)
                .build(),
            FunctionToolCallback.builder("knowledgeSearchTool", knowledgeSearchTool)
                .description("在企业知识库中搜索相关文档。适用于查询产品文档、操作手册、FAQ、政策规范、技术文档等非结构化知识。需要指定知识库编码(kbCode)和查询内容(query)。")
                .inputType(KnowledgeSearchReq.class)
                .build()
        ));

        // 添加 MCP Client 自动发现的工具（来自 decision-mcp-server）
        if (mcpToolCallbackProvider != null) {
            callbacks.addAll(Arrays.asList(mcpToolCallbackProvider.getToolCallbacks()));
        }

        return callbacks;
    }

    @Bean
    public String systemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.md");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
