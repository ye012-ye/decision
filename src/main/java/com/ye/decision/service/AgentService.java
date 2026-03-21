package com.ye.decision.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentService {

    private final ChatClient chatClient;

    public AgentService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 执行 ReAct 推理，返回流式 token。
     * ChatMemory.CONVERSATION_ID 是 Spring AI 1.0.0 中的常量（替代旧版 CHAT_MEMORY_CONVERSATION_ID_KEY）。
     */
    public Flux<String> chat(String sessionId, String message) {
        return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .content();
    }
}
