package com.ye.decision.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ye.decision.dto.ReActEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_REACT_STEPS = 10;

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final Map<String, ToolCallback> toolMap;

    public AgentService(ChatModel chatModel,
                        ChatMemory chatMemory,
                        String systemPrompt,
                        List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
        this.toolMap = toolCallbacks.stream()
            .collect(Collectors.toMap(tc -> tc.getToolDefinition().name(), Function.identity()));
    }

    /**
     * 执行 ReAct 推理循环，返回流式事件。
     * <p>
     * 循环流程：Thought → Action → Observation → ... → Answer
     * 每一步通过 ReActEvent 推送给前端，前端可按事件类型分别渲染。
     */
    public Flux<ReActEvent> chat(String sessionId, String message) {
        return Flux.create(sink -> {
            try {
                doReActLoop(sessionId, message, sink);
                sink.complete();
            } catch (Exception e) {
                log.error("ReAct loop error, sessionId={}", sessionId, e);
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void doReActLoop(String sessionId, String message, FluxSink<ReActEvent> sink) {
        // 1. 加载历史消息
        List<Message> history = chatMemory.get(sessionId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);
        messages.add(new UserMessage(message));

        // 2. 构建 ChatOptions：注册工具但禁止框架自动执行
        DashScopeChatOptions options = DashScopeChatOptions.builder()
            .toolCallbacks(toolCallbacks)
            .internalToolExecutionEnabled(false)
            .build();

        // 3. ReAct 循环
        for (int step = 0; step < MAX_REACT_STEPS; step++) {
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            AssistantMessage assistant = response.getResult().getOutput();
            messages.add(assistant);

            // 模型在调用工具前可能输出推理文本（Thought）
            String thought = assistant.getText();
            if (thought != null && !thought.isBlank() && assistant.hasToolCalls()) {
                sink.next(ReActEvent.thought(thought));
            }

            if (assistant.hasToolCalls()) {
                // Action → Observation
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
                    sink.next(ReActEvent.action(tc.name(), tc.arguments()));

                    String result = executeTool(tc);
                    sink.next(ReActEvent.observation(result));

                    toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                }
                messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
            } else {
                // 最终回答
                if (thought != null && !thought.isBlank()) {
                    sink.next(ReActEvent.answer(thought));
                }
                break;
            }
        }

        // 4. 保存本轮对话到 ChatMemory（只保存用户消息和最终助手回答）
        chatMemory.add(sessionId, List.of(new UserMessage(message), messages.get(messages.size() - 1)));
    }

    private String executeTool(AssistantMessage.ToolCall toolCall) {
        ToolCallback callback = toolMap.get(toolCall.name());
        if (callback == null) {
            log.warn("Unknown tool: {}", toolCall.name());
            return "{\"error\":\"unknown_tool\",\"message\":\"" + toolCall.name() + "\"}";
        }
        try {
            return callback.call(toolCall.arguments());
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolCall.name(), e);
            return "{\"error\":\"tool_error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
