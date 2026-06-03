package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.agent.config.AgentProperties;
import com.ye.decision.mq.ChatMemoryPublisher;
import com.ye.decision.service.McpToolRegistry;
import com.ye.decision.service.ToolCatalog;
import com.ye.decision.tool.CallExternalApiTool;
import com.ye.decision.tool.KnowledgeSearchTool;
import com.ye.decision.tool.QueryMysqlTool;
import com.ye.decision.tool.QueryRedisTool;
import com.ye.decision.tool.WorkOrderTool;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.ObjectProvider;
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

    @Bean
    public ChatMemory chatMemory(RedissonClient redissonClient,
                                 ObjectMapper objectMapper,
                                 ChatMemoryPublisher publisher,
                                 AgentProperties agentProperties) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new RedissonChatMemoryRepository(redissonClient, objectMapper, publisher))
            .maxMessages(agentProperties.getMemoryWindowSize())
            .build();
    }

    @Bean
    public ToolCatalog toolCatalog(KnowledgeSearchTool knowledgeSearchTool,
                                   QueryMysqlTool queryMysqlTool,
                                   QueryRedisTool queryRedisTool,
                                   CallExternalApiTool callExternalApiTool,
                                   WorkOrderTool workOrderTool,
                                   ObjectProvider<McpToolRegistry> mcpToolRegistryProvider) {
        List<ToolCallback> localCallbacks = List.copyOf(Arrays.asList(ToolCallbacks.from(
            knowledgeSearchTool, queryMysqlTool, queryRedisTool,
            callExternalApiTool, workOrderTool
        )));

        McpToolRegistry mcpToolRegistry = mcpToolRegistryProvider.getIfAvailable();
        return () -> {
            if (mcpToolRegistry == null) {
                return localCallbacks;
            }
            List<ToolCallback> callbacks = new ArrayList<>(localCallbacks);
            callbacks.addAll(mcpToolRegistry.getToolCallbacks());
            return callbacks;
        };
    }

    @Bean
    public String systemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.md");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
