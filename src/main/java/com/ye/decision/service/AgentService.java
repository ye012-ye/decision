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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author ye
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_REACT_STEPS = 10;

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final Map<String, ToolCallback> toolMap;
    private final ExecutorService sseExecutor;

    /**
     * 专用工具的关键词映射，用于动态工具选择。
     * Redis 和 MySQL 作为通用工具始终包含，专用工具按关键词匹配动态加入。
     */
    private static final Map<String, List<String>> TOOL_KEYWORDS = Map.of(
        "callExternalApiTool", List.of("天气", "weather", "物流", "logistics", "快递", "汇率", "exchange"),
        "knowledgeSearchTool", List.of("知识库", "文档", "手册", "faq", "政策", "规范", "knowledge", "搜索知识"),
        "mcpListTables", List.of("数据库", "有哪些表", "列出表", "table", "schema", "所有表"),
        "mcpDescribeTable", List.of("表结构", "字段", "列", "column", "describe", "索引"),
        "mcpQueryData", List.of("查询", "sql", "select", "统计", "报表", "数据分析", "聚合"),
        "mcpExecuteSql", List.of("插入", "更新", "删除", "insert", "update", "delete", "修改数据")
    );

    public AgentService(ChatModel chatModel,
                        ChatMemory chatMemory,
                        String systemPrompt,
                        List<ToolCallback> toolCallbacks,
                        ExecutorService sseExecutor) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
        this.toolMap = toolCallbacks.stream()
            .collect(Collectors.toMap(tc -> tc.getToolDefinition().name(), Function.identity()));
        this.sseExecutor = sseExecutor;
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

    /**
     * 根据用户消息动态选择相关工具，减少 LLM 选择空间和 token 消耗。
     * <p>
     * Redis 和 MySQL 作为通用数据查询工具始终包含；
     * 专用工具（外部 API、知识库搜索）仅在消息匹配相关关键词时加入。
     * 如果没有匹配到任何专用工具关键词，则回退为全部工具。
     */
    private List<ToolCallback> selectTools(String message) {
        List<ToolCallback> selected = new ArrayList<>();
        selected.add(toolMap.get("queryRedisTool"));
        selected.add(toolMap.get("queryMysqlTool"));

        String lowerMessage = message.toLowerCase();
        for (Map.Entry<String, List<String>> entry : TOOL_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lowerMessage::contains)) {
                ToolCallback cb = toolMap.get(entry.getKey());
                if (cb != null) {
                    selected.add(cb);
                }
            }
        }
        return selected;
    }

    private void doReActLoop(String sessionId, String message, FluxSink<ReActEvent> sink) {
        // 1. 加载历史消息
        List<Message> history = chatMemory.get(sessionId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);
        messages.add(new UserMessage(message));

        // 2. 动态选择工具并构建 ChatOptions
        List<ToolCallback> selectedTools = selectTools(message);
        DashScopeChatOptions options = DashScopeChatOptions.builder()
            .toolCallbacks(selectedTools)
            .internalToolExecutionEnabled(false)
            .build();

        // 用于记录本轮完整对话（包括工具交互），以便写入 ChatMemory
        List<Message> turnMessages = new ArrayList<>();
        turnMessages.add(new UserMessage(message));

        // 3. ReAct 循环
        for (int step = 0; step < MAX_REACT_STEPS; step++) {
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            AssistantMessage assistant = response.getResult().getOutput();
            messages.add(assistant);
            turnMessages.add(assistant);

            // 模型在调用工具前可能输出推理文本（Thought）
            String thought = assistant.getText();
            if (thought != null && !thought.isBlank() && assistant.hasToolCalls()) {
                sink.next(ReActEvent.thought(thought));
            }

            // 有工具调用
            if (assistant.hasToolCalls()) {
                List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();
                List<ToolResponseMessage.ToolResponse> toolResponses;

                if (toolCalls.size() == 1) {
                    // 单工具调用 — 直接执行
                    AssistantMessage.ToolCall tc = toolCalls.get(0);
                    sink.next(ReActEvent.action(tc.name(), tc.arguments()));
                    String result = executeTool(tc);
                    sink.next(ReActEvent.observation(result));
                    toolResponses = List.of(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                } else {
                    // 多工具调用 — 并行执行，降低整体延迟
                    for (AssistantMessage.ToolCall tc : toolCalls) {
                        sink.next(ReActEvent.action(tc.name(), tc.arguments()));
                    }
                    List<CompletableFuture<ToolResponseMessage.ToolResponse>> futures = toolCalls.stream()
                        .map(tc -> CompletableFuture.supplyAsync(() -> {
                            String result = executeTool(tc);
                            return new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result);
                        }, sseExecutor))
                        .toList();
                    toolResponses = futures.stream().map(CompletableFuture::join).toList();
                    for (ToolResponseMessage.ToolResponse tr : toolResponses) {
                        sink.next(ReActEvent.observation(tr.responseData()));
                    }
                }

                ToolResponseMessage trm = ToolResponseMessage.builder().responses(toolResponses).build();
                messages.add(trm);
                turnMessages.add(trm);
            } else {
                // 最终回答
                if (thought != null && !thought.isBlank()) {
                    sink.next(ReActEvent.answer(thought));
                }
                break;
            }
        }

        // 4. 保存本轮完整对话到 ChatMemory（包含工具调用过程，支持跨轮次引用）
        chatMemory.add(sessionId, turnMessages);
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
