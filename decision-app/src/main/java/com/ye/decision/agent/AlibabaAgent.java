package com.ye.decision.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentContext;
import com.ye.decision.agent.core.AgentEvent;
import com.ye.decision.agent.stream.GraphEventAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring AI Alibaba Agent Framework 的 {@link Agent} 唯一实现。
 *
 * <p>流程：读 ChatMemory → 拼 messages → router.stream → 适配为 AgentEvent → 终态写回 ChatMemory。</p>
 */
public class AlibabaAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AlibabaAgent.class);

    private final LlmRoutingAgent rootRouter;
    private final ChatMemory chatMemory;

    public AlibabaAgent(LlmRoutingAgent rootRouter, ChatMemory chatMemory) {
        this.rootRouter = rootRouter;
        this.chatMemory = chatMemory;
    }

    @Override
    public Flux<AgentEvent> chat(AgentContext context) {
        String sessionId = context.sessionId();
        UserMessage userMessage = new UserMessage(context.userMessage());

        List<Message> history = safeHistory(sessionId);
        List<Message> input = new ArrayList<>(history.size() + 1);
        input.addAll(history);
        input.add(userMessage);

        // 累积本轮模型输出，终态时写回 ChatMemory
        List<Message> turn = new ArrayList<>();
        turn.add(userMessage);

        Flux<NodeOutput> raw;
        try {
            raw = rootRouter.stream(input);
        } catch (Exception e) {
            log.error("Failed to start agent stream, sessionId={}", sessionId, e);
            return Flux.just(AgentEvent.error(e.getMessage()), AgentEvent.done());
        }

        Flux<NodeOutput> withCapture = raw.doOnNext(node -> captureMessages(node, turn));
        return GraphEventAdapter.toEvents(withCapture)
            .doOnComplete(() -> persistTurn(sessionId, turn))
            .doOnError(err -> log.error("Agent stream error, sessionId={}", sessionId, err));
    }

    private List<Message> safeHistory(String sessionId) {
        try {
            return chatMemory.get(sessionId);
        } catch (Exception e) {
            log.warn("Failed to load chat memory, sessionId={}, falling back to empty", sessionId, e);
            return List.of();
        }
    }

    private void captureMessages(NodeOutput node, List<Message> turn) {
        if (node.state() == null) {
            return;
        }
        Object msgs = node.state().value("messages").orElse(null);
        if (!(msgs instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof AssistantMessage am && !turn.contains(am)) {
                turn.add(am);
            } else if (item instanceof ToolResponseMessage trm && !turn.contains(trm)) {
                turn.add(trm);
            }
        }
    }

    private void persistTurn(String sessionId, List<Message> turn) {
        try {
            chatMemory.add(sessionId, turn);
        } catch (Exception e) {
            log.warn("Failed to persist turn to ChatMemory, sessionId={}", sessionId, e);
        }
    }
}
