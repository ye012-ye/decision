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
