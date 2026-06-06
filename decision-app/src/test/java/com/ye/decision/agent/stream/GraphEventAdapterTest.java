package com.ye.decision.agent.stream;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.ye.decision.agent.core.AgentEvent;
import com.ye.decision.agent.core.AgentEventType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link GraphEventAdapter} 纯单元测试 —— 用合成的 NodeOutput/StreamingOutput 序列驱动，
 * 不依赖 DashScope / Nacos。重点验证：token 增量流式发 ANSWER、reasoning 发 THOUGHT、
 * {@code __END__} 不与流式增量重复、以及无增量时的整段兜底。
 */
class GraphEventAdapterTest {

    private static final String CHAT_LEAF = "subgraph_chat";

    private static OverAllState stateWith(List<Message> messages) {
        return new OverAllState(Map.of("messages", messages));
    }

    private static OverAllState emptyState() {
        return stateWith(List.of());
    }

    private static NodeOutput routeStart() {
        return NodeOutput.of("__START__", CHAT_LEAF, emptyState(), null).setSubGraph(true);
    }

    private static NodeOutput modelDelta(AssistantMessage delta) {
        NodeOutput out = new StreamingOutput<Object>((Message) delta, "_AGENT_MODEL_", CHAT_LEAF, emptyState());
        return out.setSubGraph(true);
    }

    private static NodeOutput routerEnd(List<Message> messages) {
        return NodeOutput.of("__END__", "root-router", stateWith(messages), null);
    }

    private static List<AgentEvent> run(NodeOutput... nodes) {
        return GraphEventAdapter.toEvents(Flux.fromArray(nodes)).collectList().block();
    }

    @Test
    void streamsAnswerTokenByTokenAndAppendsDone() {
        List<AgentEvent> events = run(
            routeStart(),
            modelDelta(new AssistantMessage("你好")),
            modelDelta(new AssistantMessage("，我是")),
            modelDelta(new AssistantMessage("助手")),
            routerEnd(List.of(new UserMessage("hi"), new AssistantMessage("你好，我是助手")))
        );

        assertThat(events)
            .extracting(AgentEvent::type, AgentEvent::payload)
            .containsExactly(
                tuple(AgentEventType.ROUTE, "chat"),
                tuple(AgentEventType.ANSWER, "你好"),
                tuple(AgentEventType.ANSWER, "，我是"),
                tuple(AgentEventType.ANSWER, "助手"),
                tuple(AgentEventType.DONE, "")
            );
    }

    @Test
    void doesNotResendFullAnswerAtEndWhenAlreadyStreamed() {
        List<AgentEvent> events = run(
            modelDelta(new AssistantMessage("partial")),
            routerEnd(List.of(new AssistantMessage("partial and more")))
        );

        // __END__ 不再整段补发，避免前端拼成重复内容
        assertThat(events)
            .extracting(AgentEvent::type)
            .containsExactly(AgentEventType.ANSWER, AgentEventType.DONE);
        assertThat(events.get(0).payload()).isEqualTo("partial");
    }

    @Test
    void fallsBackToFullAnswerAtEndWhenNothingStreamed() {
        // 框架未surface token增量（StreamingOutput 不携带文本）时，仍要保证有完整答案
        List<AgentEvent> events = run(
            routeStart(),
            routerEnd(List.of(new UserMessage("hi"), new AssistantMessage("完整答案")))
        );

        assertThat(events)
            .extracting(AgentEvent::type, AgentEvent::payload)
            .containsExactly(
                tuple(AgentEventType.ROUTE, "chat"),
                tuple(AgentEventType.ANSWER, "完整答案"),
                tuple(AgentEventType.DONE, "")
            );
    }

    @Test
    void emitsThoughtFromReasoningContentDelta() {
        AssistantMessage reasoning = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", "让我想想"))
            .build();

        List<AgentEvent> events = run(modelDelta(reasoning));

        assertThat(events)
            .extracting(AgentEvent::type, AgentEvent::payload)
            .containsExactly(
                tuple(AgentEventType.THOUGHT, "让我想想"),
                tuple(AgentEventType.DONE, "")
            );
    }

    @Test
    void skipsAnswerForToolCallDecisionDelta() {
        AssistantMessage toolDecision = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"北京\"}")))
            .build();

        List<AgentEvent> events = run(modelDelta(toolDecision));

        // 仅有 DONE —— 决定调工具的增量不产出 ANSWER
        assertThat(events)
            .extracting(AgentEvent::type)
            .containsExactly(AgentEventType.DONE);
    }

    @Test
    void emitsActionAndObservationOnToolRound() {
        AssistantMessage toolCall = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"北京\"}")))
            .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "{\"temp\":\"22°C\"}")))
            .build();
        NodeOutput toolNode = NodeOutput.of("_AGENT_TOOL_", "subgraph_weather",
            stateWith(List.of(new UserMessage("北京天气"), toolCall, toolResponse)), null)
            .setSubGraph(true);

        List<AgentEvent> events = run(toolNode);

        assertThat(events)
            .extracting(AgentEvent::type, AgentEvent::payload)
            .containsExactly(
                tuple(AgentEventType.ACTION, "getWeather | {\"city\":\"北京\"}"),
                tuple(AgentEventType.OBSERVATION, "{\"temp\":\"22°C\"}"),
                tuple(AgentEventType.DONE, "")
            );
    }
}
