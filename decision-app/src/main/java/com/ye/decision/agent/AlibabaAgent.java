package com.ye.decision.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentContext;
import com.ye.decision.agent.core.AgentEvent;
import com.ye.decision.agent.hooks.ChatMemoryAgentHook;
import com.ye.decision.agent.stream.GraphEventAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 基于 Spring AI Alibaba Agent Framework 的 {@link Agent} 唯一实现。
 *
 * <p>流程：创建 RunnableConfig → router.stream → 适配为 AgentEvent。ChatMemory 由根路由 hook 读写。</p>
 * @author ye
 */
public class AlibabaAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AlibabaAgent.class);
    /**
     *  根路由器
     */
    private final LlmRoutingAgent rootRouter;

    public AlibabaAgent(LlmRoutingAgent rootRouter) {
        this.rootRouter = rootRouter;
    }

    @Override
    public Flux<AgentEvent> chat(AgentContext context) {
        String sessionId = sessionId(context);
        String requestId = UUID.randomUUID().toString();

        String message = context.userMessage();
        if (context.userContext() != null) {
            var uc = context.userContext();
            message = "[当前用户: %s (%s, 角色: %s)]\n\n%s".formatted(
                uc.nickname(), uc.username(), uc.role(), message);
        }
        UserMessage userMessage = new UserMessage(message);

        RunnableConfig config = RunnableConfig.builder()
            .threadId(sessionId)
            .addMetadata(ChatMemoryAgentHook.SESSION_ID_METADATA_KEY, sessionId)
            .addMetadata(ChatMemoryAgentHook.REQUEST_ID_METADATA_KEY, requestId)
            .addMetadata("userMessage", context.userMessage())
            .build();

        Flux<NodeOutput> raw;
        try {
            raw = rootRouter.stream(userMessage, config);
        } catch (Exception e) {
            log.error("Failed to start agent stream, sessionId={}", sessionId, e);
            return Flux.just(AgentEvent.error(e.getMessage()), AgentEvent.done());
        }

        return GraphEventAdapter.toEvents(raw)
            .doOnError(err -> log.error("Agent stream error, sessionId={}, requestId={}", sessionId, requestId, err))
            .onErrorResume(err -> Flux.just(AgentEvent.error(err.getMessage()), AgentEvent.done()));
    }

    private String sessionId(AgentContext context) {
        if (context.sessionId() == null || context.sessionId().isBlank()) {
            return ChatMemory.DEFAULT_CONVERSATION_ID;
        }
        return context.sessionId();
    }
}
