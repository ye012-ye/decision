package com.ye.decision.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AiConfig {

    @Value("${decision.agent.memory-window-size:10}")
    private int memoryWindowSize;

    /**
     * Redis-backed ChatMemory via Redisson.
     * Messages are persisted as JSON lists at "chat:memory:{conversationId}".
     * The MessageChatMemoryAdvisor reads/writes via this repository on every turn.
     */
    @Bean
    public ChatMemory chatMemory(RedissonClient redissonClient, ObjectMapper objectMapper) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new RedissonChatMemoryRepository(redissonClient, objectMapper))
            .maxMessages(memoryWindowSize)
            .build();
    }

    @Bean
    @RefreshScope
    public ChatClient chatClient(DashScopeChatModel chatModel, ChatMemory chatMemory, String systemPrompt) {
        return ChatClient.builder(chatModel)
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultToolNames("queryMysqlTool", "queryRedisTool", "callExternalApiTool")
            .build();
    }

    @Bean
    public String systemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.md");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
