package com.ye.decision.agent.hooks;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMemoryAgentHookTest {

    @Test
    void injectsHistoryBeforeAgent() throws Exception {
        InMemoryChatMemory memory = new InMemoryChatMemory();
        memory.add("s1", List.of(new UserMessage("old"), new AssistantMessage("old answer")));
        ChatMemoryAgentHook hook = new ChatMemoryAgentHook(memory);
        RunnableConfig config = RunnableConfig.builder().threadId("s1").build();

        AgentCommand command = hook.beforeAgent(List.of(new UserMessage("now")), config);

        assertThat(messages(command)).extracting(Message::getText)
            .containsExactly("old", "old answer", "now");
        assertThat(config.context()).containsEntry(ChatMemoryAgentHook.HISTORY_SIZE_CONTEXT_KEY, 2);
    }

    @Test
    void persistsOnlyMessagesAfterExistingHistory() {
        InMemoryChatMemory memory = new InMemoryChatMemory();
        memory.add("s1", List.of(new UserMessage("old"), new AssistantMessage("old answer")));
        ChatMemoryAgentHook hook = new ChatMemoryAgentHook(memory);
        RunnableConfig config = RunnableConfig.builder().threadId("s1").build();
        hook.beforeAgent(List.of(new UserMessage("now")), config);

        hook.afterAgent(List.of(
            new UserMessage("old"),
            new AssistantMessage("old answer"),
            new UserMessage("now"),
            new AssistantMessage("new answer")
        ), config);

        assertThat(memory.get("s1")).extracting(Message::getText)
            .containsExactly("old", "old answer", "now", "new answer");
    }

    @SuppressWarnings("unchecked")
    private static List<Message> messages(AgentCommand command) throws Exception {
        Method method = AgentCommand.class.getDeclaredMethod("getMessages");
        method.setAccessible(true);
        return (List<Message>) method.invoke(command);
    }

    private static class InMemoryChatMemory implements ChatMemory {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void add(String conversationId, List<Message> messages) {
            this.messages.addAll(messages);
        }

        @Override
        public List<Message> get(String conversationId) {
            return List.copyOf(messages);
        }

        @Override
        public void clear(String conversationId) {
            messages.clear();
        }
    }
}
