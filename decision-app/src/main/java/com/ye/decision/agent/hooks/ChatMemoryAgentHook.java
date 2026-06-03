package com.ye.decision.agent.hooks;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class ChatMemoryAgentHook extends MessagesAgentHook {

    public static final String SESSION_ID_METADATA_KEY = "sessionId";
    public static final String REQUEST_ID_METADATA_KEY = "requestId";
    static final String HISTORY_SIZE_CONTEXT_KEY = "decision.chat_memory.history_size";

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryAgentHook.class);

    private final ChatMemory chatMemory;
    private ReactAgent agent;

    public ChatMemoryAgentHook(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public AgentCommand beforeAgent(List<Message> previousMessages, RunnableConfig config) {
        String sessionId = sessionId(config);
        List<Message> history = safeHistory(sessionId);
        config.context().put(HISTORY_SIZE_CONTEXT_KEY, history.size());
        if (history.isEmpty()) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }

        List<Message> merged = new ArrayList<>(history.size() + previousMessages.size());
        merged.addAll(history);
        for (Message message : previousMessages) {
            if (!merged.contains(message)) {
                merged.add(message);
            }
        }
        return new AgentCommand(List.copyOf(merged), UpdatePolicy.REPLACE);
    }

    @Override
    public AgentCommand afterAgent(List<Message> previousMessages, RunnableConfig config) {
        String sessionId = sessionId(config);
        int historySize = historySize(config);
        int start = Math.min(historySize, previousMessages.size());
        List<Message> turn = previousMessages.subList(start, previousMessages.size());
        if (!turn.isEmpty()) {
            persistTurn(sessionId, turn);
        }
        return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
    }

    @Override
    public String getName() {
        return "ChatMemory";
    }

    @Override
    public ReactAgent getAgent() {
        return agent;
    }

    @Override
    public void setAgent(ReactAgent agent) {
        this.agent = agent;
    }

    @Override
    public int getOrder() {
        return Prioritized.HIGHEST_PRECEDENCE;
    }

    private String sessionId(RunnableConfig config) {
        return config.threadId()
            .or(() -> config.metadata(SESSION_ID_METADATA_KEY).map(String::valueOf))
            .orElse(ChatMemory.DEFAULT_CONVERSATION_ID);
    }

    private int historySize(RunnableConfig config) {
        Object value = config.context().get(HISTORY_SIZE_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private List<Message> safeHistory(String sessionId) {
        try {
            return chatMemory.get(sessionId);
        } catch (Exception e) {
            log.warn("Failed to load chat memory, sessionId={}, falling back to empty", sessionId, e);
            return List.of();
        }
    }

    private void persistTurn(String sessionId, List<Message> turn) {
        try {
            chatMemory.add(sessionId, List.copyOf(turn));
        } catch (Exception e) {
            log.warn("Failed to persist turn to ChatMemory, sessionId={}", sessionId, e);
        }
    }
}
