package com.ye.decision.agent.stream;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.ye.decision.agent.core.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 Spring AI Alibaba Graph 的 {@link NodeOutput} 流翻译成项目 {@link AgentEvent} 流。
 *
 * <p>实现根据 spike (NODE_OUTPUT_NOTES.md) 实测得到的事件序列：</p>
 * <ul>
 *   <li>ROUTE — 首个 {@code __START__} 且 {@code isSubGraph()==true} 的事件，
 *       {@code agent()} 形如 {@code "subgraph_<leafName>"}，剥前缀即域名。</li>
 *   <li>THOUGHT / ACTION / OBSERVATION — 单个 {@code _AGENT_TOOL_} 事件
 *       原子地携带 {@code AssistantMessage(toolCalls)} + {@code ToolResponseMessage}，
 *       一次扇出 1×THOUGHT(可空) + N×ACTION + N×OBSERVATION。</li>
 *   <li>ANSWER — 外层 router {@code __END__}（{@code isSubGraph()==false}），
 *       从 {@code state.messages} 取最后一条无 toolCalls 的 {@code AssistantMessage}。</li>
 *   <li>DONE — 流正常结束后追加。</li>
 * </ul>
 */
public final class GraphEventAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphEventAdapter.class);

    private static final String SUBGRAPH_PREFIX = "subgraph_";
    private static final String NODE_AGENT_TOOL = "_AGENT_TOOL_";
    private static final String NODE_END = "__END__";

    private GraphEventAdapter() {}

    public static Flux<AgentEvent> toEvents(Flux<NodeOutput> nodeOutputs) {
        AdapterState state = new AdapterState();
        return nodeOutputs.flatMap(node -> Flux.fromIterable(translate(node, state)))
            .onErrorResume(err -> {
                log.error("Graph stream failed", err);
                return Flux.just(AgentEvent.error(err.getMessage()));
            })
            .concatWith(Flux.just(AgentEvent.done()));
    }

    private static List<AgentEvent> translate(NodeOutput node, AdapterState s) {
        List<AgentEvent> events = new ArrayList<>();
        String nodeName = node.node() == null ? "" : node.node();
        String agent = node.agent() == null ? "" : node.agent();

        // ROUTE — 首次进入 subgraph
        if (node.isSTART() && node.isSubGraph()
            && agent.startsWith(SUBGRAPH_PREFIX) && !s.routeEmitted) {
            events.add(AgentEvent.route(agent.substring(SUBGRAPH_PREFIX.length())));
            s.routeEmitted = true;
        }

        List<Message> msgs = readMessages(node);

        // tool round — 一次原子事件展开 thought + action + observation
        if (NODE_AGENT_TOOL.equals(nodeName) && msgs != null) {
            emitToolRound(msgs, events);
        }

        // 外层 router END — 取最终答案
        if (NODE_END.equals(nodeName) && !node.isSubGraph() && msgs != null) {
            emitFinalAnswer(msgs, events);
        }

        return events;
    }

    private static void emitToolRound(List<Message> msgs, List<AgentEvent> out) {
        AssistantMessage assistantCall = null;
        ToolResponseMessage tail = null;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Message m = msgs.get(i);
            if (tail == null && m instanceof ToolResponseMessage tr) {
                tail = tr;
                continue;
            }
            if (m instanceof AssistantMessage am && am.hasToolCalls()) {
                assistantCall = am;
                break;
            }
        }
        if (assistantCall != null) {
            String thought = assistantCall.getText();
            if (thought != null && !thought.isBlank()) {
                out.add(AgentEvent.thought(thought));
            }
            for (AssistantMessage.ToolCall tc : assistantCall.getToolCalls()) {
                out.add(AgentEvent.action(tc.name(), tc.arguments()));
            }
        }
        if (tail != null) {
            for (ToolResponseMessage.ToolResponse resp : tail.getResponses()) {
                out.add(AgentEvent.observation(resp.responseData()));
            }
        }
    }

    private static void emitFinalAnswer(List<Message> msgs, List<AgentEvent> out) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i) instanceof AssistantMessage am && !am.hasToolCalls()) {
                String text = am.getText();
                if (text != null && !text.isBlank()) {
                    out.add(AgentEvent.answer(text));
                }
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Message> readMessages(NodeOutput node) {
        if (node.state() == null) {
            return null;
        }
        Object v = node.state().data().get("messages");
        if (!(v instanceof List<?> list) || list.isEmpty()) {
            return v instanceof List<?> ? List.of() : null;
        }
        for (Object o : list) {
            if (!(o instanceof Message)) {
                return null;
            }
        }
        return (List<Message>) v;
    }

    private static final class AdapterState {
        boolean routeEmitted = false;
    }
}
