# Spring AI Alibaba Agent Hook Refactor Design

**Date:** 2026-05-07
**Status:** Implemented in working tree

## Goal

Keep the existing `LlmRoutingAgent -> ReactAgent` topology and package structure, remove the layered split proposal, and move model/runtime behavior onto Spring AI Alibaba Agent Framework APIs.

## Key Decisions

- Upgrade Spring AI Alibaba to `1.1.2.2`.
- Keep `agent/core`, `agent/domains`, `agent/router`, and `agent/config`.
- Do not create new architecture contracts, service layers, or infrastructure adapters.
- Use `RunnableConfig` for session/request metadata.
- Use a root `MessagesAgentHook` to load and persist chat messages through the existing Redis-backed `ChatMemory`.
- Use `SkillsAgentHook` for every domain `ReactAgent`.
- Keep business tools in `ToolCatalog.byNames(...)`; add the `read_skill` tool from Alibaba's hook.
- Use Alibaba model/tool limit hooks and tool interceptors instead of hand-written loop guards.

## Runtime Flow

```text
ChatController
  -> AlibabaAgent
      -> RunnableConfig(threadId=sessionId, metadata=requestId/sessionId/userMessage)
      -> root LlmRoutingAgent
          -> ChatMemoryAgentHook.beforeAgent loads history from Redis ChatMemory
          -> RoutingNode chooses a domain ReactAgent
          -> domain ReactAgent uses SkillsAgentHook and ToolCatalog tools
          -> ChatMemoryAgentHook.afterAgent persists only new turn messages
      -> GraphEventAdapter keeps SSE event names unchanged
```

## Alibaba APIs Used

- `LlmRoutingAgent.builder().hooks(...)`
- `ReactAgent.builder().hooks(...)`
- `ReactAgent.builder().toolContext(...)`
- `ReactAgent.builder().parallelToolExecution(...)`
- `ReactAgent.builder().maxParallelTools(...)`
- `ReactAgent.builder().toolExecutionTimeout(...)`
- `ReactAgent.builder().wrapSyncToolsAsAsync(...)`
- `RunnableConfig.builder().threadId(...).addMetadata(...)`
- `MessagesAgentHook`
- `SkillsAgentHook`
- `ClasspathSkillRegistry`
- `ModelCallLimitHook`
- `ToolCallLimitHook`
- `ToolRetryInterceptor`
- `ToolErrorInterceptor`

## Files Added

- `decision-app/src/main/java/com/ye/decision/agent/config/AgentProperties.java`
- `decision-app/src/main/java/com/ye/decision/agent/hooks/ChatMemoryAgentHook.java`
- `decision-app/src/main/java/com/ye/decision/agent/hooks/AgentHookFactory.java`
- `decision-app/src/main/java/com/ye/decision/agent/skills/AgentSkillRegistryFactory.java`
- `decision-app/src/main/java/com/ye/decision/agent/skills/SingleSkillRegistry.java`
- `decision-app/src/main/resources/skills/*/SKILL.md`

## Compatibility

- Frontend SSE event names remain unchanged.
- Existing local tools and MCP tools remain selected through `ToolCatalog.byNames(...)`.
- Redis persistence still uses `RedissonChatMemoryRepository`; the read/write trigger moved from `AlibabaAgent` into `ChatMemoryAgentHook`.
- `decision.agent.*` config now controls memory window, routing fallback, hooks, skills, and tool execution.
