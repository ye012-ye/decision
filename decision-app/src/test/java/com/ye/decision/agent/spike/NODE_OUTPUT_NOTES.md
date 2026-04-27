# NodeOutput shape — observations from spike run

Date: 2026-04-27
Framework: spring-ai-alibaba-agent-framework 1.1.2.0 (graph-core 1.1.2.0)
Test: `AgentFrameworkSpike#exploreNodeOutputShape` (slim, no SpringBoot — DashScopeChatModel built directly from env var)
Routing model: DashScope qwen-plus (default)
Topology: `LlmRoutingAgent("root-router")` -> single `ReactAgent("echo")` leaf

## NodeOutput class hierarchy seen

Two concrete classes were emitted on the Flux:

- `com.alibaba.cloud.ai.graph.NodeOutput` — used for boundary events (`__START__`, `__END__`).
- `com.alibaba.cloud.ai.graph.streaming.StreamingOutput` (extends `NodeOutput`) — used for in-flight node events, especially the LLM streaming token deltas of `_AGENT_MODEL_`.

Note: there is **no separate "tool" or "answer" subclass**. Token streaming is detectable purely by `instanceof StreamingOutput`. Tool calls (none in this spike) appear inside `messages` as `AssistantMessage.toolCalls` / `ToolResponseMessage` — see "Implications" below.

## Sequence of nodes (16 events for one query)

| # | class | node | agent | START/END | sub | notes |
|---|-------|------|-------|-----------|-----|-------|
| 1 | NodeOutput | `__START__` | `root-router` | START | no | initial state with `input`, `messages`, `_graph_execution_id_` |
| 2 | StreamingOutput | `root-router` | `root-router` | - | no | router node entered |
| 3 | StreamingOutput | `root-router_routing` | `root-router` | - | no | **routing decision node** |
| 4 | NodeOutput | `__START__` | `subgraph_echo` | START | **yes** | sub-graph for routed leaf agent starts; agent name is prefixed `subgraph_<leafName>` |
| 5–14 | StreamingOutput | `_AGENT_MODEL_` | `subgraph_echo` | - | yes | **LLM token stream chunks** (one per delta — 10 chunks here) |
| 15 | NodeOutput | `__END__` | `subgraph_echo` | END | yes | leaf finishes; `messages` now contains the final `AssistantMessage` |
| 16 | NodeOutput | `__END__` | `root-router` | END | no | router finishes; same `AssistantMessage` propagated up |

Log line printed by the framework between #3 and #4: `RoutingAgent root-router routed to single sub-agent echo.`

## Routing decision

- Emitted by node `root-router_routing` (event #3). Convention: `<routerName>_routing`.
- The routing target name **was not surfaced as a state key** in this run. State at #3 still only has `[_graph_execution_id_, input, messages]`. The routing target is only knowable from:
  - the framework log line (`RoutingAgent ... routed to single sub-agent <name>.`)
  - the next event's `agent()` value being `subgraph_<targetName>`.
- For a multi-leaf scenario: GraphEventAdapter should infer the route by watching for the **first `__START__` event whose `isSubGraph()==true`**, and reading `agent()` (strip `subgraph_` prefix). That is the most reliable signal.

## Message accumulation pattern

- `state.data["messages"]` is a `List<Message>` that **grows monotonically** through the run.
- Initial: `[UserMessage]`
- Inside subgraph: `[UserMessage, AgentInstructionMessage]` (the leaf's `instruction()` is appended as a USER-typed `AgentInstructionMessage`)
- Final (in `__END__`): `[UserMessage, AgentInstructionMessage, AssistantMessage]`
- The `AssistantMessage` carries `textContent`, `toolCalls` (empty in this spike), `metadata.finishReason`, `metadata.id`, and `metadata.reasoningContent`.
- `state.data["input"]` holds the original raw user text as a String.

**Critical**: `StreamingOutput` events #5–#14 carry the **full state snapshot** but the `AssistantMessage` is **NOT yet present** in `messages` during streaming — it only appears at the subgraph's `__END__`. So token deltas during streaming have to be read from a different surface (likely `streamMessages()` or a streaming hook) — they are NOT directly readable from `NodeOutput.state()`.

This is a non-obvious finding. We may need to use `Agent#streamMessages(...)` in parallel for token deltas, OR cast to `StreamingOutput` and look for a chunk accessor — needs a follow-up sub-spike if we want token-level streaming to the frontend.

## Final answer

- The final `AssistantMessage` first becomes visible in event #15 (`__END__`, `agent=subgraph_echo`, `isSubGraph=true`).
- It is then mirrored unchanged in event #16 (`__END__`, `agent=root-router`, `isSubGraph=false`).
- Picking #15 vs #16 is a wash semantically; **#16 is the cleanest signal of "the whole thing is done"** since it's the outer router END.

## Raw sample (one event of each kind, redacted)

```
=== [1] NodeOutput class=com.alibaba.cloud.ai.graph.NodeOutput node=__START__ agent=root-router isSTART=true isEND=false isSubGraph=false
    state.keys=[_graph_execution_id_, input, messages]
    state.data={_graph_execution_id_=<uuid>, input=<user text>, messages=[UserMessage{content='<user text>', ...}]}

=== [3] NodeOutput class=com.alibaba.cloud.ai.graph.streaming.StreamingOutput node=root-router_routing agent=root-router ...
    state.keys=[_graph_execution_id_, input, messages]   // routing target NOT in state yet

(framework log) RoutingAgent root-router routed to single sub-agent echo.

=== [4] NodeOutput class=com.alibaba.cloud.ai.graph.NodeOutput node=__START__ agent=subgraph_echo isSTART=true isEND=false isSubGraph=true
    // First event where the routed leaf is identifiable: agent="subgraph_<leafName>"

=== [5..14] StreamingOutput node=_AGENT_MODEL_ agent=subgraph_echo isSubGraph=true
    // 10 token-stream events; messages list does NOT yet contain the AssistantMessage

=== [15] NodeOutput node=__END__ agent=subgraph_echo isSubGraph=true
    state.data.messages = [..., AssistantMessage [textContent='<final answer>', toolCalls=[], metadata={finishReason=STOP, id=..., reasoningContent=}]]

=== [16] NodeOutput node=__END__ agent=root-router isSubGraph=false
    state.data.messages = [same as #15]
```

## Implications for GraphEventAdapter

1. **ROUTE event**: emit on the first `NodeOutput` with `isSTART() && isSubGraph()`. Extract leaf name = `agent().removePrefix("subgraph_")`. (Do NOT rely on a `route_target` state key — there is none.)

2. **THOUGHT / ACTION / OBSERVATION**: not directly observable in this spike (no tools used). Need a follow-up sub-spike that wires a tool into the `echo` ReactAgent to confirm. Hypothesis: tool calls show up as `AssistantMessage.toolCalls` non-empty in the `messages` list at `__END__` of an inner iteration, and `ToolResponseMessage` instances will appear in `messages` after the tool runs. The `_AGENT_MODEL_` node will likely fire multiple times per turn for the ReAct loop — one per LLM call.

3. **ANSWER event**: emit on the outer router `__END__` (event #16). Read the last `AssistantMessage` from `state.data["messages"]`. This gives the whole answer cleanly.

4. **Token-level streaming to the SSE frontend**: `NodeOutput.state()` does NOT carry per-chunk text. To stream tokens we need either (a) `Agent#streamMessages()` in parallel, or (b) access the chunk text inside `StreamingOutput` (need to inspect `StreamingOutput`'s public API — not done in this spike). If acceptable, the simplest path is to skip token streaming and just emit the final answer at #16. The legacy SSE contract emits `answer` chunks, so we should decide before T6 whether to keep that or switch to single-shot `answer` events.

5. **Done signal**: `node()=="__END__" && !isSubGraph()` (i.e. event #16-style) is the clean terminal marker. Any earlier END is a sub-graph end and does NOT mean the whole run is over.

## Tool flow observations (sub-spike #2)

Test: `AgentFrameworkSpike#exploreToolCallNodeOutputShape`. Setup: same `LlmRoutingAgent` -> single `ReactAgent("weatherbot")` leaf. Leaf has a single `FunctionToolCallback("getWeather", WeatherReq -> "{...}")`. Prompt forces tool use: `"请用工具查一下北京今天的天气"`. The LLM did call the tool (one round), got the JSON back, then produced a final natural-language Chinese answer.

Surprising headline: **same total NodeOutput count (16) as the no-tool spike**, despite a tool round-trip happening. The framework folds the tool turn into the same envelope of events; what changes is which node names appear and where the message list grows.

### Sequence of nodes seen (16 events, tool path)

| # | class | node | agent | START/END | sub | messages.size | tail class |
|---|-------|------|-------|-----------|-----|---------------|------------|
| 1 | NodeOutput | `__START__` | `root-router` | START | no | 1 | UserMessage |
| 2 | StreamingOutput | `root-router` | `root-router` | - | no | 1 | UserMessage |
| 3 | StreamingOutput | `root-router_routing` | `root-router` | - | no | 1 | UserMessage |
| 4 | NodeOutput | `__START__` | `subgraph_weatherbot` | START | yes | 2 | AgentInstructionMessage |
| 5 | StreamingOutput | `_AGENT_MODEL_` | `subgraph_weatherbot` | - | yes | 2 | AgentInstructionMessage |
| 6 | StreamingOutput | `_AGENT_MODEL_` | `subgraph_weatherbot` | - | yes | 2 | AgentInstructionMessage |
| **7** | StreamingOutput | **`_AGENT_TOOL_`** | `subgraph_weatherbot` | - | yes | **4** | **ToolResponseMessage** |
| 8–14 | StreamingOutput | `_AGENT_MODEL_` | `subgraph_weatherbot` | - | yes | 4 | ToolResponseMessage |
| 15 | NodeOutput | `__END__` | `subgraph_weatherbot` | END | yes | 5 | AssistantMessage (no toolCalls) |
| 16 | NodeOutput | `__END__` | `root-router` | END | no | 3 | AssistantMessage (no toolCalls) |

Framework log between #3 and #4: `RoutingAgent root-router routed to single sub-agent weatherbot.`

### THOUGHT detection
- **Source**: NOT directly observable as a discrete event. The "thinking" phase corresponds to the streaming `_AGENT_MODEL_` chunks (#5–#6 in the first round, #8–#14 in the second round). The `AssistantMessage` carrying that thought is **NOT in `state.data["messages"]`** during these chunks — it only commits to state when the next node fires.
- **Pattern**: We can interpret each contiguous run of `_AGENT_MODEL_` events on the same `agent()` as a thought-streaming phase, and emit `THOUGHT` start on the first chunk, `THOUGHT` end on the next non-`_AGENT_MODEL_` event of the same agent. To get the actual *text*, we'd need `StreamingOutput`'s chunk accessor (out of scope), OR we read the committed AssistantMessage at the **next node's** state and back-fill (but if the next node is `_AGENT_TOOL_`, the AssistantMessage with `toolCalls` is already in messages — see ACTION below).

### ACTION detection
- **Source**: Event #7, `node="_AGENT_TOOL_"`. This is the event where the tool actually executed.
- **Pattern**: When a NodeOutput arrives with `node()=="_AGENT_TOOL_"`, walk `state.data["messages"]` from the tail backwards to find:
  - The **last `AssistantMessage` with `hasToolCalls()==true`** — this gives the tool name + arguments (the THOUGHT-to-ACTION transition).
  - The **last `ToolResponseMessage`** (which is `messages.tail()` itself at this point) — this gives the tool result.
- **Critical**: at event #7 the messages list jumped from size 2 to size 4 in a single step — both the `AssistantMessage(toolCalls=[...])` and the resulting `ToolResponseMessage` were appended atomically. We never see a state snapshot with only the AssistantMessage and not yet the response. So **THOUGHT-with-toolCall and OBSERVATION are co-emitted**, not separable by node-boundary events.

### OBSERVATION detection
- **Source**: same event #7 (`_AGENT_TOOL_`). The `ToolResponseMessage` is the tail of `state.data["messages"]`.
- **Pattern**: `messages.get(messages.size()-1) instanceof ToolResponseMessage`. Read `responses[0].name` (= tool name) and `responses[0].responseData` (= the JSON returned by the tool function). One `_AGENT_TOOL_` node may carry multiple `ToolResponse` entries if the LLM called multiple tools in one turn (not exercised in this spike).

### ANSWER detection
- **Source**: same as no-tool case — outer router `__END__` (event #16, `agent="root-router"`, `isSubGraph=false`). The final `AssistantMessage` (with `toolCalls=[]` and `finishReason=STOP`) is `messages.tail()`.
- **Note**: the outer state's `messages.size=3` while the subgraph's was `size=5` — meaning the subgraph's `AgentInstructionMessage`, the AssistantMessage(toolCalls), and the ToolResponseMessage are **NOT** propagated to the parent graph state. Only `[UserMessage, ?, AssistantMessage(final)]` survive at the router level. So intermediate tool events MUST be captured from the subgraph events (#7), not reconstructed at the end from the router END.

### Implications for GraphEventAdapter — tool path

1. **Per-node dispatch table**: branch on `n.node()` rather than just isSTART/isEND. The relevant node names so far: `__START__`, `__END__`, `<routerName>` (router self), `<routerName>_routing`, `_AGENT_MODEL_`, `_AGENT_TOOL_`. We should defensively log unknown node names — there may be others (planner, evaluator, etc.) once we use richer agents.

2. **THOUGHT/ACTION/OBSERVATION are not 1:1 with NodeOutput events**: a single `_AGENT_TOOL_` event carries BOTH the assistant's tool-call decision AND the tool's response in `messages`. The adapter should, on `_AGENT_TOOL_`:
   - emit one `THOUGHT` event (text from the AssistantMessage immediately preceding the ToolResponseMessage; may be empty if model only emitted tool_calls without text),
   - emit one `ACTION` event per `ToolCall` in that AssistantMessage (`toolName | arguments`),
   - emit one `OBSERVATION` event per `ToolResponse` in the tail ToolResponseMessage.

   These three events are derived from the same single NodeOutput. Don't try to fan them across multiple NodeOutputs.

3. **Mid-LLM token streaming is still not visible** through `state()`. The repeated `_AGENT_MODEL_` events (#8–#14 here) carry no new committed text — they're token chunks. If we want token-level `answer` SSE chunks, we still need to peek `StreamingOutput`'s chunk content (separate sub-spike).

4. **Subgraph END (#15) vs router END (#16)**: prefer #15 for the answer text — its `messages` is the full leaf history (5 messages) and `tail` is the final AssistantMessage. #16's outer `messages.size=3` is also fine since `tail` is the same AssistantMessage, but #15 is the cleaner signal that the leaf has produced its answer. For the legacy SSE contract (`done` event after `answer`), use #16.

### Raw sample (the load-bearing event)

```
=== [7] NodeOutput class=com.alibaba.cloud.ai.graph.streaming.StreamingOutput node=_AGENT_TOOL_ agent=subgraph_weatherbot isSTART=false isEND=false isSubGraph=true
    state.keys=[input, _graph_execution_id_, messages]
    messages.size=4 tail.class=org.springframework.ai.chat.messages.ToolResponseMessage
    tail=ToolResponseMessage{responses=[ToolResponse[id=call_212c81b8b4bc4738aff109, name=getWeather, responseData={"city":"北京","temp":"22°C","condition":"sunny"}]], messageType=TOOL, metadata={messageType=TOOL}}
```

At this event, `messages` is `[UserMessage, AgentInstructionMessage, AssistantMessage(toolCalls=[ToolCall(name=getWeather, args={"city":"北京"})], text=""), ToolResponseMessage(...)]`. THOUGHT (empty text), ACTION (getWeather + args), and OBSERVATION (JSON result) all derive from this one NodeOutput.

```
=== [15] NodeOutput node=__END__ agent=subgraph_weatherbot isSubGraph=true
    messages.size=5 tail=AssistantMessage [textContent=北京今天天气晴朗，气温为22摄氏度。, toolCalls=[], finishReason=STOP, ...]
```

Final answer arrives in the subgraph END; outer router END (#16) mirrors it but with a smaller messages list (intermediate subgraph messages do not propagate up).
