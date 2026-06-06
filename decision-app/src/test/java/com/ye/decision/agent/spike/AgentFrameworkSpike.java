package com.ye.decision.agent.spike;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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

    @Test
    void exploreToolCallNodeOutputShape() throws Exception {
        ChatModel chatModel = buildChatModel();

        ToolCallback weatherTool = FunctionToolCallback.builder("getWeather",
                (Function<WeatherReq, String>) req ->
                    "{\"city\":\"" + req.city() + "\",\"temp\":\"22°C\",\"condition\":\"sunny\"}")
            .description("Returns the current weather for a city. Input field: city (string).")
            .inputType(WeatherReq.class)
            .build();

        ReactAgent leaf = ReactAgent.builder()
            .name("weatherbot")
            .description("Looks up the current weather for a city using the getWeather tool.")
            .model(chatModel)
            .instruction("You are a weather assistant. Whenever the user asks about the weather of a city, you MUST call the getWeather tool with the city name. After receiving the tool result, summarize it in Chinese.")
            .tools(List.of(weatherTool))
            .build();

        LlmRoutingAgent router = LlmRoutingAgent.builder()
            .name("root-router")
            .description("Routes user messages to the weather specialist.")
            .model(chatModel)
            .subAgents(List.<com.alibaba.cloud.ai.graph.agent.Agent>of(leaf))
            .build();

        AtomicInteger seq = new AtomicInteger(0);
        Flux<NodeOutput> stream = router.stream(List.<Message>of(new UserMessage("请用工具查一下北京今天的天气")));
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
                    Object messages = n.state().data().get("messages");
                    if (messages instanceof List<?> list && !list.isEmpty()) {
                        Object tail = list.get(list.size() - 1);
                        System.out.println("    messages.size=" + list.size()
                                + " tail.class=" + tail.getClass().getName()
                                + " tail=" + tail);
                    } else {
                        System.out.println("    state.data=" + n.state().data());
                    }
                }
            } catch (Throwable t) {
                System.out.println("    state inspect threw: " + t);
            }
        }).blockLast();

        System.out.println("=== TOTAL NodeOutputs emitted: " + seq.get());
    }

    /**
     * Sub-spike #3 — dump the per-chunk surface of every {@link StreamingOutput} so we can
     * pin down (a) WHERE the token text lives: {@code message()} / {@code chunk()} /
     * {@code getOriginData()} (ChatResponse), (b) whether each chunk is an incremental DELTA
     * or the CUMULATIVE text-so-far, and (c) whether reasoning/"thinking" streams via
     * {@code reasoningContent}. These three are the assumptions baked into GraphEventAdapter's
     * token-streaming path — confirm/adjust against this output.
     */
    @Test
    void exploreStreamingChunkShape() throws Exception {
        ChatModel chatModel = buildChatModel();

        ToolCallback weatherTool = FunctionToolCallback.builder("getWeather",
                (Function<WeatherReq, String>) req ->
                    "{\"city\":\"" + req.city() + "\",\"temp\":\"22°C\",\"condition\":\"sunny\"}")
            .description("Returns the current weather for a city. Input field: city (string).")
            .inputType(WeatherReq.class)
            .build();

        ReactAgent leaf = ReactAgent.builder()
            .name("weatherbot")
            .description("Looks up the current weather for a city using the getWeather tool.")
            .model(chatModel)
            .instruction("You are a weather assistant. Whenever the user asks about the weather of a city, you MUST call the getWeather tool with the city name. After receiving the tool result, summarize it in Chinese.")
            .tools(List.of(weatherTool))
            .build();

        LlmRoutingAgent router = LlmRoutingAgent.builder()
            .name("root-router")
            .description("Routes user messages to the weather specialist.")
            .model(chatModel)
            .subAgents(List.<com.alibaba.cloud.ai.graph.agent.Agent>of(leaf))
            .build();

        AtomicInteger seq = new AtomicInteger(0);
        Flux<NodeOutput> stream = router.stream(List.<Message>of(new UserMessage("请用工具查一下北京今天的天气")));
        stream.doOnNext(n -> {
            int i = seq.incrementAndGet();
            if (!(n instanceof StreamingOutput<?> so)) {
                System.out.println("=== [" + i + "] NodeOutput(non-streaming) node=" + n.node() + " agent=" + n.agent());
                return;
            }
            System.out.println("=== [" + i + "] StreamingOutput node=" + so.node() + " agent=" + so.agent()
                    + " outputType=" + so.getOutputType());
            System.out.println("    chunk()=" + quote(so.chunk()));
            Message msg = so.message();
            if (msg instanceof AssistantMessage am) {
                System.out.println("    message=AssistantMessage hasToolCalls=" + am.hasToolCalls()
                        + " text=" + quote(am.getText())
                        + " reasoningContent=" + quote(reasoningOf(am)));
            } else {
                System.out.println("    message=" + (msg == null ? "null" : msg.getClass().getSimpleName()));
            }
            Object origin = so.getOriginData();
            if (origin instanceof ChatResponse cr && cr.getResult() != null) {
                AssistantMessage out = cr.getResult().getOutput();
                System.out.println("    originData=ChatResponse output.text=" + quote(out == null ? null : out.getText())
                        + " reasoningContent=" + quote(out == null ? null : reasoningOf(out)));
            } else {
                System.out.println("    originData=" + (origin == null ? "null" : origin.getClass().getSimpleName()));
            }
        }).blockLast();

        System.out.println("=== TOTAL NodeOutputs emitted: " + seq.get());
    }

    private static String quote(String s) {
        if (s == null) {
            return "<null>";
        }
        return "[" + s.length() + "]'" + s + "'";
    }

    private static String reasoningOf(AssistantMessage am) {
        if (am.getMetadata() == null) {
            return null;
        }
        Object rc = am.getMetadata().get("reasoningContent");
        return rc == null ? null : rc.toString();
    }

    public record WeatherReq(String city) {}

    private ChatModel buildChatModel() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        DashScopeApi api = DashScopeApi.builder().apiKey(key).build();
        return DashScopeChatModel.builder().dashScopeApi(api).build();
    }
}
