# Spring AI Alibaba DDD Agent 增量重构设计

**日期**: 2026-05-07
**状态**: Draft (待评审)
**范围选择**: A - 基于现有 Agent Framework 增量重构
**关联**: `docs/superpowers/specs/2026-04-27-springai-alibaba-agent-refactor-design.md`

---

## 1. 背景

当前项目已经完成一轮 Spring AI Alibaba Agent Framework 改造：

- 根路由为 `LlmRoutingAgent`
- 领域专家为 5 个 `ReactAgent`: `knowledge` / `data` / `workorder` / `external` / `chat`
- 工具通过 `ToolCatalog.byNames(...)` 按领域白名单注入
- MCP 数据库工具通过 `McpToolRegistry` 供 `data` Agent 使用
- SSE 事件仍为 `route` / `thought` / `action` / `observation` / `answer` / `done` / `error`

项目仍存在几个问题：

- 根 POM 使用 `spring-ai-alibaba.version=1.1.2.0`，不是当前可用的最新稳定版本 `1.1.2.2`。
- `agent/` 包已经有多 Agent 结构，但仍混合了应用编排、领域声明、Spring AI Alibaba 基础设施细节。
- `SkillsAgentHook`、`SkillsInterceptor`、模型/工具调用限制 Hook、ToolContext 等新版 Agent Framework 能力尚未接入。
- 项目只有传统 `domain/entity/dto` 包，并未形成清晰的 DDD 边界；本轮先让 Agent 子系统具备可演进边界，不做全项目大搬迁。

---

## 2. 目标与非目标

### 2.1 目标

1. 升级 Spring AI Alibaba 到 `1.1.2.2`，使用新版 Agent Framework API。
2. 保留现有 `LlmRoutingAgent -> ReactAgent` 拓扑，避免推倒重来。
3. 将 Agent 子系统增量重构为 DDD 风格边界：
   - `interfaces`
   - `application`
   - `domain`
   - `infrastructure`
4. 接入并统一管理：
   - Hooks
   - Skills
   - Tools
   - ToolContext
   - Interceptors
5. 保持前端 SSE 事件契约兼容。
6. 让工具缺失、skill 缺失等配置问题在启动期 fail-fast。

### 2.2 非目标

- 不重写 `decision-web`。
- 不重排整个 `decision-app` 的所有业务包。
- 不重构 `decision-mcp-server` 的内部 DDD 边界。
- 不新增数据库审计表；本轮先通过日志和 ToolContext 元数据实现可观测性。
- 不引入 feature flag 或双轨 Agent；现有多 Agent 拓扑继续作为唯一实现。

---

## 3. 版本与 API 策略

根 POM 统一管理 Spring AI Alibaba 版本：

```xml
<spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
```

`decision-app/pom.xml` 中 Alibaba 相关依赖不再显式写版本，统一由 `spring-ai-alibaba-bom` 管理：

- `spring-ai-alibaba-starter-dashscope`
- `spring-ai-alibaba-agent-framework`

本轮要使用的新版能力：

| 能力 | API |
|------|-----|
| Agent Hook | `com.alibaba.cloud.ai.graph.agent.hook.AgentHook` / `ModelHook` |
| Skills | `SkillsAgentHook` + `FileSystemSkillRegistry` 或 `ClasspathSkillRegistry` |
| Skills 拦截 | `SkillsInterceptor` |
| 工具限制 | `ToolCallLimitHook` |
| 模型调用限制 | `ModelCallLimitHook` |
| 工具治理 | `ToolInterceptor` / `ToolRetryInterceptor` / `ToolErrorInterceptor` |
| ToolContext | `org.springframework.ai.chat.model.ToolContext` + `ToolContextHelper` |
| 并行/超时 | `ReactAgent.builder().parallelToolExecution(...)` / `toolExecutionTimeout(...)` |

---

## 4. 架构边界

本轮只在 Agent 子系统内增量形成 DDD 分层。

```
decision-app/src/main/java/com/ye/decision/agent/
├─ interfaces/
│  └─ stream/
│     └─ GraphEventAdapter.java
├─ application/
│  ├─ AlibabaAgent.java
│  ├─ AgentRunContext.java
│  └─ AgentMemoryService.java
├─ domain/
│  ├─ Agent.java
│  ├─ AgentContext.java
│  ├─ AgentEvent.java
│  ├─ AgentEventType.java
│  ├─ DomainAgent.java
│  ├─ DomainAgentSpec.java
│  └─ DomainToolPolicy.java
└─ infrastructure/
   ├─ config/
   │  └─ AgentConfig.java
   ├─ framework/
   │  ├─ ReactDomainAgent.java
   │  ├─ ReactAgentFactory.java
   │  └─ RouterAgentFactory.java
   ├─ hooks/
   │  └─ AgentHookFactory.java
   ├─ skills/
   │  └─ AgentSkillRegistryFactory.java
   └─ tools/
      ├─ AgentToolContextFactory.java
      └─ AgentToolPolicyFactory.java
```

迁移采取兼容式小步：

- 先新增新包和适配类。
- 再把现有 `agent/core/*`、`agent/stream/*`、`agent/router/*`、`agent/config/*` 迁移到新边界。
- 领域 Agent 类保留 `knowledge/data/workorder/external/chat` 语义，后续可以逐步移动到 `agent/domain/domains/*` 或保留在现有路径并实现 `DomainAgentSpec`。

---

## 5. 核心组件

| 组件 | 层 | 职责 |
|------|----|------|
| `Agent` | domain | 对外聊天用例抽象，保持 `Flux<AgentEvent> chat(AgentContext context)` |
| `AlibabaAgent` | application | 编排一次 Agent run：读记忆、执行路由、事件适配、写记忆 |
| `AgentRunContext` | application | 保存 `sessionId`、`requestId`、用户输入、开始时间、当前路由目标 |
| `AgentMemoryService` | application | 封装 `ChatMemory` 读取、追加和异常降级 |
| `DomainAgentSpec` | domain | 声明领域名、描述、系统提示、skill 名、工具白名单 |
| `DomainToolPolicy` | domain | 声明工具调用限制、超时、是否允许并行、重试策略 |
| `ReactDomainAgent` | infrastructure | 把 `DomainAgentSpec` 转为 Spring AI Alibaba `ReactAgent` |
| `ReactAgentFactory` | infrastructure | 统一接入 hooks、skills、interceptors、tools、tool context |
| `RouterAgentFactory` | infrastructure | 用所有领域 spec 构造 `LlmRoutingAgent` |
| `AgentHookFactory` | infrastructure | 生成 `SkillsAgentHook`、`ModelCallLimitHook`、`ToolCallLimitHook` |
| `AgentSkillRegistryFactory` | infrastructure | 生成 classpath 或 filesystem skill registry |
| `AgentToolContextFactory` | infrastructure | 为工具调用提供 `sessionId/requestId/agentName` 等元数据 |

---

## 6. Skills 设计

Skills 放在 classpath 资源目录，和应用一起打包：

```
decision-app/src/main/resources/skills/
├─ knowledge/SKILL.md
├─ data/SKILL.md
├─ workorder/SKILL.md
├─ external/SKILL.md
└─ chat/SKILL.md
```

每个领域只加载自己的 skill，避免把所有业务规则塞进系统提示。

### 6.1 skill 内容原则

每个 `SKILL.md` 控制在领域级说明，不写实现细节：

- 领域职责
- 何时使用本领域
- 允许调用的工具
- 输入信息不足时如何追问
- 工具返回异常时如何向用户解释
- 输出风格和业务约束

### 6.2 加载策略

优先使用 `ClasspathSkillRegistry`，因为 skills 随应用发布，部署一致性更高。

后续若需要热更新，再扩展为 `FileSystemSkillRegistry`，通过 `decision.agent.skills.mode=filesystem` 控制。

---

## 7. Hooks 与 Interceptors 设计

每个 `ReactAgent` 由 `ReactAgentFactory` 统一装配：

```java
ReactAgent.builder()
    .name(spec.name())
    .description(spec.description())
    .model(chatModel)
    .instruction(spec.systemPrompt())
    .tools(tools)
    .hooks(hooks)
    .interceptors(interceptors)
    .parallelToolExecution(policy.parallelToolExecution())
    .maxParallelTools(policy.maxParallelTools())
    .toolExecutionTimeout(policy.toolExecutionTimeout())
    .build();
```

### 7.1 默认 hooks

| Hook | 用途 |
|------|------|
| `SkillsAgentHook` | 在 Agent 运行前注入 skill 索引和 read skill 工具 |
| `ModelCallLimitHook` | 限制单轮模型调用次数，防止无限循环 |
| `ToolCallLimitHook` | 限制单个工具或整体工具调用次数 |

### 7.2 默认 interceptors

| Interceptor | 用途 |
|-------------|------|
| `SkillsInterceptor` | 模型调用前按需扩展 skill 内容 |
| `ToolRetryInterceptor` | 对临时失败工具做有限重试 |
| `ToolErrorInterceptor` | 将工具异常转成 LLM 可理解的结构化错误 |

本轮不启用 Human-in-the-loop Hook；如果未来工单关闭、SQL 写操作需要人工确认，再单独设计。

---

## 8. Tools 与 ToolContext 设计

现有工具注册方式保留：

- 本地工具仍由 `AiConfig.toolCatalog(...)` 注册 `FunctionToolCallback`
- MCP 工具仍由 `McpToolRegistry` 发现并合并到 `ToolCatalog`
- 领域工具仍通过 `ToolCatalog.byNames(...)` 精确筛选

新增 `AgentToolContextFactory`，把运行元数据注入工具上下文：

```java
Map<String, Object> toolContext = Map.of(
    "sessionId", runContext.sessionId(),
    "requestId", runContext.requestId(),
    "agentName", spec.name(),
    "userMessage", runContext.userMessage()
);
```

工具和拦截器可以通过 `ToolContextHelper` 读取这些字段，用于：

- 日志审计
- 错误定位
- 运行时限流
- 后续扩展业务审计表

---

## 9. 领域 Agent 策略

| 领域 | 工具 | Skill |
|------|------|-------|
| `knowledge` | `knowledgeSearchTool` | `knowledge/SKILL.md` |
| `data` | `queryRedisTool`, `queryMysqlTool`, `listTables`, `describeTable`, `queryData`, `executeSql` | `data/SKILL.md` |
| `workorder` | `workOrderTool` | `workorder/SKILL.md` |
| `external` | `callExternalApiTool` | `external/SKILL.md` |
| `chat` | 无业务工具，只保留 read skill 工具 | `chat/SKILL.md` |

`data` 领域继续在启动时调用 `mcpToolRegistry.refreshNow()`，确保 MCP 工具存在后再装配 `ReactAgent`。

---

## 10. 数据流

```
ChatController
  -> Agent.chat(AgentContext)
  -> AlibabaAgent
      1. 创建 AgentRunContext(requestId/sessionId/userMessage)
      2. AgentMemoryService 读取 ChatMemory
      3. root LlmRoutingAgent 选择领域
      4. 领域 ReactAgent 运行
          - SkillsAgentHook 注入 skill 索引
          - SkillsInterceptor 按需扩展 skill
          - ToolContext 注入运行元数据
          - Tool/Model Hook 限制调用次数
      5. GraphEventAdapter 转 SSE 事件
      6. AgentMemoryService 写回本轮消息
```

前端事件名不变：

- `route`
- `thought`
- `action`
- `observation`
- `answer`
- `done`
- `error`

---

## 11. 配置项

扩展 `decision.agent.*`：

```yaml
decision:
  agent:
    memory-window-size: 20
    router:
      fallback-agent: chat
    model-call-limit:
      run-limit: 8
    tool-call-limit:
      run-limit: 12
    tools:
      parallel-execution: true
      max-parallel-tools: 4
      execution-timeout: 30s
    skills:
      mode: classpath
      classpath-path: skills
      fail-fast: true
```

`fail-fast=true` 时：

- skill 目录不存在，启动失败
- 领域声明的 skill 不存在，启动失败
- 领域声明的工具不存在，启动失败

---

## 12. 错误处理

| 场景 | 处理 |
|------|------|
| Spring AI Alibaba 依赖解析失败 | 构建失败，修正 BOM/仓库配置 |
| 领域工具缺失 | 启动期抛 `IllegalStateException` |
| skill 缺失 | 启动期抛 `IllegalStateException` |
| MCP server 未就绪 | `dataAgent` 装配失败，保持现有 fail-fast |
| 模型调用超限 | 推 `error` 事件，随后推 `done` |
| 工具调用超限 | 推 `error` 或由 LLM 收到结构化错误后总结 |
| 工具异常 | `ToolErrorInterceptor` 转为结构化错误 |
| SSE 流异常 | 不直接断开，返回 `error + done` |
| ChatMemory 读取失败 | 记录 warn，使用空历史继续 |
| ChatMemory 写入失败 | 记录 warn，不影响本轮响应返回 |

---

## 13. 测试策略

### 13.1 单元测试

新增或调整测试：

- `DomainAgentSpecTest`
  - 验证领域名、工具白名单、skill 名完整
- `AgentSkillRegistryFactoryTest`
  - 验证 classpath skills 可以加载
  - 验证缺失 skill 时 fail-fast
- `AgentHookFactoryTest`
  - 验证 `SkillsAgentHook`、`ModelCallLimitHook`、`ToolCallLimitHook` 被装配
- `ReactAgentFactoryTest`
  - 验证工具白名单、hook、interceptor、并行工具配置被传入 builder
- `GraphEventAdapterTest`
  - 继续覆盖 route/tool/final/error/done 映射
- `ToolCatalogTest`
  - 保留并增强 byNames 缺失工具失败测试

### 13.2 集成测试

保留现有 `AlibabaAgentIT`，继续由 `DASHSCOPE_API_KEY` 控制是否运行。

新增不依赖真实 DashScope 的 Spring 装配测试：

- `AgentConfigurationTest`
  - 使用 mock `ChatModel`
  - 使用静态工具 catalog
  - 验证 5 个领域 agent 和 root router 可创建
  - 验证 classpath skills 被加载

### 13.3 验证命令

```bash
rtk .\mvnw.cmd -pl decision-app test
```

如果本地外部依赖阻塞，只运行离线测试并说明：

```bash
rtk .\mvnw.cmd -pl decision-app -Dtest=AgentConfigurationTest,AgentSkillRegistryFactoryTest,AgentHookFactoryTest test
```

---

## 14. 迁移步骤

1. 升级根 POM 的 `spring-ai-alibaba.version` 到 `1.1.2.2`。
2. 移除 `decision-app/pom.xml` 中 Alibaba 依赖的显式 `<version>`。
3. 新增 `agent/domain` 抽象：`DomainAgentSpec`、`DomainToolPolicy`。
4. 新增 `agent/infrastructure/skills`：classpath skill registry 工厂。
5. 新增 `skills/*/SKILL.md` 资源。
6. 新增 `agent/infrastructure/hooks`：统一 Hook 工厂。
7. 新增 `agent/infrastructure/tools`：ToolContext 和工具策略工厂。
8. 将 `AbstractDomainAgent` 改为只声明领域 spec，`ReactAgent` 构建下沉到 `ReactAgentFactory`。
9. 将 `RouterAgentFactory` 移到 infrastructure，并改为基于 `DomainAgentSpec` 构造路由提示。
10. 将 `AlibabaAgent` 移到 application，拆出 `AgentMemoryService`。
11. 将 `GraphEventAdapter` 移到 interfaces/stream，保持事件契约不变。
12. 更新 `AgentConfig` 装配所有新工厂。
13. 补齐单元测试和装配测试。
14. 跑 Maven 测试并修复编译或 API 兼容问题。

---

## 15. 风险与缓解

| 风险 | 缓解 |
|------|------|
| `1.1.2.2` API 与 `1.1.2.0` 行为差异 | 先跑最小装配测试和现有 spike，所有框架 API 使用本地 jar 签名确认 |
| Skills 注入导致提示词过长 | 每个领域只注册自己的 skill，skill 内容保持短小 |
| Hook/Interceptor 顺序影响工具调用 | 通过 `AgentHookFactoryTest` 和真实 tool-call spike 验证 |
| DDD 拆包过大引入回归 | 先只重构 Agent 子系统，业务 service/mapper 不大搬迁 |
| MCP server 未启动导致本地测试困难 | 保持现有 fail-fast，同时离线装配测试使用静态 ToolCatalog |
| SSE 流式 token 仍非逐 token | 保持现有事件契约，最终答案仍通过 `answer` 事件输出；不在本轮追求 token 级重构 |

---

## 16. 完成标准

本轮重构完成时应满足：

- `spring-ai-alibaba.version` 为 `1.1.2.2`。
- 5 个领域 Agent 全部通过 `ReactAgentFactory` 统一装配。
- 每个领域都有对应 `SKILL.md`。
- `SkillsAgentHook`、`SkillsInterceptor`、模型调用限制、工具调用限制至少在默认装配中启用。
- 工具调用携带 `sessionId/requestId/agentName` ToolContext 元数据。
- 前端 SSE 事件名不变。
- 离线装配测试通过。
- 可运行环境下 `AlibabaAgentIT` 保持通过或给出明确外部依赖阻塞说明。

