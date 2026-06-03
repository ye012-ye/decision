# Spring AI Alibaba Agent Hook Refactor Plan

> This plan supersedes the removed layered refactor plan. Keep the current package layout and implement Alibaba-native hooks, skills, tools, and runtime config.

## Tasks

- [x] Upgrade `spring-ai-alibaba.version` to `1.1.2.2`.
- [x] Add typed `decision.agent.*` configuration.
- [x] Add classpath skills under `decision-app/src/main/resources/skills`.
- [x] Add a root `ChatMemoryAgentHook` based on Alibaba `MessagesAgentHook`.
- [x] Move message history load/persist out of `AlibabaAgent`.
- [x] Pass `RunnableConfig` with `threadId`, `sessionId`, `requestId`, and `userMessage`.
- [x] Attach `SkillsAgentHook`, `ModelCallLimitHook`, and `ToolCallLimitHook` to domain `ReactAgent`s.
- [x] Attach Alibaba tool retry/error interceptors and tool execution settings.
- [x] Keep `ToolCatalog.byNames(...)` for business tool selection.
- [x] Add focused tests for properties, skill registry, and chat memory hook.

## Verification

```powershell
rtk .\mvnw.cmd -pl decision-app -DskipTests compile
rtk .\mvnw.cmd -pl decision-app "-Dtest=AgentPropertiesTest,AgentSkillRegistryFactoryTest,ChatMemoryAgentHookTest" test
```
