package com.ye.decision.service;

import com.ye.decision.domain.dto.ReActEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    ChatModel chatModel = mock(ChatModel.class);
    ChatMemory chatMemory = mock(ChatMemory.class);

    @Test
    void chat_returnsAnswerWhenNoToolCalls() {
        // 模型直接返回文本，不调用工具
        AssistantMessage assistant = new AssistantMessage("你好，有什么可以帮你的？");
        ChatResponse response = new ChatResponse(List.of(new Generation(assistant)));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        when(chatMemory.get(any())).thenReturn(List.of());

        AgentService service = new AgentService(chatModel, chatMemory, "你是助手", List::of, null);

        StepVerifier.create(service.chat("s1", "hi"))
            .expectNext(ReActEvent.answer("你好，有什么可以帮你的？"))
            .verifyComplete();

        verify(chatMemory).add(eq("s1"), anyList());
    }

    @Test
    void chat_executesToolAndReturnsAnswer() {
        // 第一轮：模型请求调用工具
        AssistantMessage withToolCall = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall("tc1", "function", "mockTool", "{\"q\":\"test\"}")))
            .build();
        ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(withToolCall)));

        // 第二轮：模型返回最终回答
        AssistantMessage finalAnswer = new AssistantMessage("查询结果是 42");
        ChatResponse answerResponse = new ChatResponse(List.of(new Generation(finalAnswer)));

        when(chatModel.call(any(Prompt.class)))
            .thenReturn(toolCallResponse)
            .thenReturn(answerResponse);
        when(chatMemory.get(any())).thenReturn(List.of());

        // 构造 mock ToolCallback
        ToolCallback toolCallback = mock(ToolCallback.class);
        ToolDefinition toolDef = ToolDefinition.builder()
            .name("mockTool")
            .description("mock")
            .inputSchema("{}")
            .build();
        when(toolCallback.getToolDefinition()).thenReturn(toolDef);
        when(toolCallback.call(anyString())).thenReturn("{\"result\":42}");

        AgentService service = new AgentService(chatModel, chatMemory, "你是助手", () -> List.of(toolCallback), null);

        StepVerifier.create(service.chat("s1", "查询"))
            .expectNext(ReActEvent.action("mockTool", "{\"q\":\"test\"}"))
            .expectNext(ReActEvent.observation("{\"result\":42}"))
            .expectNext(ReActEvent.answer("查询结果是 42"))
            .verifyComplete();
    }

    @Test
    void chat_usesLatestToolCatalogInsteadOfConstructorSnapshot() {
        AssistantMessage withToolCall = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall("tc1", "function", "dynamicTool", "{\"q\":\"later\"}")))
            .build();
        ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(withToolCall)));

        AssistantMessage finalAnswer = new AssistantMessage("动态工具已执行");
        ChatResponse answerResponse = new ChatResponse(List.of(new Generation(finalAnswer)));

        when(chatModel.call(any(Prompt.class)))
            .thenReturn(toolCallResponse)
            .thenReturn(answerResponse);
        when(chatMemory.get(any())).thenReturn(List.of());

        ToolCallback toolCallback = mock(ToolCallback.class);
        ToolDefinition toolDef = ToolDefinition.builder()
            .name("dynamicTool")
            .description("dynamic")
            .inputSchema("{}")
            .build();
        when(toolCallback.getToolDefinition()).thenReturn(toolDef);
        when(toolCallback.call(anyString())).thenReturn("{\"result\":\"ok\"}");

        AtomicReference<List<ToolCallback>> toolCallbacks = new AtomicReference<>(List.of());
        AgentService service = new AgentService(chatModel, chatMemory, "你是助手", toolCallbacks::get, null);

        toolCallbacks.set(List.of(toolCallback));

        StepVerifier.create(service.chat("s1", "查询"))
            .expectNext(ReActEvent.action("dynamicTool", "{\"q\":\"later\"}"))
            .expectNext(ReActEvent.observation("{\"result\":\"ok\"}"))
            .expectNext(ReActEvent.answer("动态工具已执行"))
            .verifyComplete();
    }
}
