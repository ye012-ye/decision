package com.ye.decision.agent.spike;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Slim spike — does NOT use @SpringBootTest. Builds DashScopeChatModel directly
 * to bypass Nacos/MySQL/etc. Goal: observe the real shape of NodeOutput emitted
 * by LlmRoutingAgent + ReactAgent so we can design GraphEventAdapter.
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class AgentFrameworkSpike {

    @Test
    void exploreNodeOutputShape() throws Exception {
        ChatModel chatModel = buildChatModel();

        ReactAgent leaf = ReactAgent.builder()
            .name("echo")
            .description("Echoes the user message back briefly in Chinese.")
            .model(chatModel)
            .instruction("You are a friendly echo bot. Reply briefly in Chinese, no tools.")
            .build();

        LlmRoutingAgent router = LlmRoutingAgent.builder()
            .name("root-router")
            .description("Routes user messages to the right specialist (only one here).")
            .model(chatModel)
            .subAgents(List.<com.alibaba.cloud.ai.graph.agent.Agent>of(leaf))
            .build();

        AtomicInteger seq = new AtomicInteger(0);
        Flux<NodeOutput> stream = router.stream(List.<Message>of(new UserMessage("你好，简单介绍一下你自己")));
        stream.doOnNext(n -> {
            int i = seq.incrementAndGet();
            System.out.println("=== [" + i + "] NodeOutput"
                    + " class=" + n.getClass().getName()
                    + " node=" + n.node()
                    + " agent=" + n.agent()
                    + " isSTART=" + n.isSTART()
                    + " isEND=" + n.isEND()
                    + " isSubGraph=" + n.isSubGraph());
            try {
                if (n.state() != null) {
                    System.out.println("    state.keys=" + n.state().data().keySet());
                    System.out.println("    state.data=" + n.state().data());
                }
            } catch (Throwable t) {
                System.out.println("    state inspect threw: " + t);
            }
        }).blockLast();

        System.out.println("=== TOTAL NodeOutputs emitted: " + seq.get());
    }

    private ChatModel buildChatModel() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        DashScopeApi api = DashScopeApi.builder().apiKey(key).build();
        return DashScopeChatModel.builder().dashScopeApi(api).build();
    }
}
