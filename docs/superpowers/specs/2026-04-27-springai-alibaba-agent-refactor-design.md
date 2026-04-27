# Spring AI Alibaba Agent Framework 重构设计

**日期**: 2026-04-27
**作者**: ye_wj
**状态**: Draft (待评审)
**关联**: [2026-03-20-spring-ai-agent-design.md](2026-03-20-spring-ai-agent-design.md)

---

## 1. 背景与目标

`decision-app` 当前的 Agent 实现（`service.AgentService`）是一段手写的 ReAct 循环，
配合静态 `TOOL_KEYWORDS` 关键词映射做工具过滤。问题：

- **强耦合**：所有工具混在一个 Agent 内，靠关键词硬编码做"工具选择"，不准且难维护。
- **难扩展**：新增业务域需要在 `TOOL_KEYWORDS` 加映射，并修改单一巨型类。
- **难测试**：循环、流、工具执行、记忆管理糅合在一个类里。
- **重复造轮**：`ReactAgent` 的能力（步数限制、流式输出、工具执行编排）框架已经提供。

**目标**：用 Spring AI Alibaba Agent Framework (1.1.2.0) 重构为
**路由 + 域专家子 Agent** 多智能体拓扑，使结构清晰、可扩展、可解耦。

非目标：替换前端框架；改动 RAG 子系统内部；改 `decision-mcp-server`。

---

## 2. 总体架构

```
                       ┌─────────────────────────┐
   POST /api/chat ───▶ │   ChatController        │  (SSE 入口，session 管理)
                       └────────────┬────────────┘
                                    │ Flux<AgentEvent>
                       ┌────────────▼────────────┐
                       │   AlibabaAgent          │  (实现 Agent 接口)
                       │   - 装配根 RouterAgent  │
                       │   - 流适配 Graph→Event  │
                       └────────────┬────────────┘
                                    │
                       ┌────────────▼────────────┐
                       │  RouterAgent            │  (LlmRoutingAgent)
                       │  让 LLM 选哪个子 Agent  │
                       └─┬──┬──┬──┬──┬───────────┘
                         │  │  │  │  │
              ┌──────────┘  │  │  │  └────────────┐
              ▼             ▼  ▼  ▼               ▼
        Knowledge      Data  WorkOrder External  Chat
        Agent          Agent Agent     Agent     Agent
        (RAG)          (SQL/ (工单)    (天气/    (兜底)
                       MCP)            物流)
```

### 2.1 核心组件

| 组件 | 职责 |
|------|------|
| `Agent` (接口) | 不变：`Flux<AgentEvent> chat(sessionId, message)` |
| `AlibabaAgent` | 唯一实现，组装 RouterAgent、写 ChatMemory、把 Graph 流适配为事件流 |
| `RouterAgent` | 基于 `LlmRoutingAgent`，根据用户消息+历史选择目标子 Agent |
| `AbstractDomainAgent` | 域 Agent 基类，封装 `ReactAgent` 装配 + 共享 ChatMemory + 系统提示 |
| `KnowledgeAgent` | 持有 `knowledgeSearchTool`，处理 RAG 知识库问答 |
| `DataAgent` | 持有 `queryRedisTool` + `queryMysqlTool` + 4 个 MCP 数据库工具 |
| `WorkOrderAgent` | 持有 `workOrderTool`，处理工单全生命周期 |
| `ExternalApiAgent` | 持有 `callExternalApiTool`，处理天气/物流/汇率等 |
| `ChatAgent` | 无工具，纯 LLM 兜底（路由失败或闲聊） |
| `GraphEventAdapter` | Spring AI Alibaba Graph `NodeOutput` 流 → 项目 `AgentEvent` 流 |
| `RouterAgentFactory` | 用所有 `AbstractDomainAgent` Bean 自动构建路由器 |

### 2.2 解耦原则

1. **子 Agent 之间零相互依赖**：仅依赖 `core/` 抽象 + 自己的工具集
2. **无 `TOOL_KEYWORDS`**：路由由 LLM 决定，工具按域分配
3. **新增域的成本**：一个新子包 + `AgentConfig` 加几行 = 完成

---

## 3. 包结构

```
decision-app/src/main/java/com/ye/decision/
├─ agent/                              ← 新增（旧 service 里的 Agent 相关全部迁过来）
│  ├─ core/
│  │   ├─ Agent.java                   (从 service/ 搬过来)
│  │   ├─ AgentEvent.java              (替代 ReActEvent)
│  │   ├─ AgentEventType.java          (enum: ROUTE/THOUGHT/ACTION/OBSERVATION/ANSWER/DONE/ERROR)
│  │   ├─ AgentContext.java            (record: sessionId, userMessage, history)
│  │   └─ AbstractDomainAgent.java
│  ├─ stream/
│  │   └─ GraphEventAdapter.java
│  ├─ memory/
│  │   └─ SharedChatMemoryConfig.java  (装配现有 RedissonChatMemoryRepository)
│  ├─ router/
│  │   └─ RouterAgentFactory.java
│  ├─ domains/
│  │   ├─ knowledge/
│  │   │   ├─ KnowledgeAgent.java
│  │   │   └─ KnowledgePrompts.java
│  │   ├─ data/
│  │   │   ├─ DataAgent.java
│  │   │   └─ DataPrompts.java
│  │   ├─ workorder/
│  │   │   ├─ WorkOrderAgent.java
│  │   │   └─ WorkOrderPrompts.java
│  │   ├─ external/
│  │   │   ├─ ExternalApiAgent.java
│  │   │   └─ ExternalPrompts.java
│  │   └─ chat/
│  │       ├─ ChatAgent.java
│  │       └─ ChatPrompts.java
│  ├─ AlibabaAgent.java
│  └─ config/
│      └─ AgentConfig.java             (装配整个 Agent 拓扑)
│
├─ service/                            ← 清理
│  ├─ ToolCatalog.java                 (保留；新增 byNames(String...) 方法)
│  ├─ McpToolRegistry.java             (保留)
│  └─ [删除] AgentService.java
│  └─ [删除] AgentAliService.java
│  └─ [删除] Agent.java                 (移到 agent/core/)
│
├─ domain/dto/
│  └─ [删除] ReActEvent.java            (被 agent/core/AgentEvent 替代)
│
└─ controller/
   └─ ChatController.java              (改注入 Agent → AlibabaAgent；事件序列化更新)
```

### 3.1 约定

- 域 Agent **不直接 `@Component`**：避免自动扫描装配；统一在 `AgentConfig` 显式 `@Bean`，
  谁组合谁可见。
- 工具仍在 `com.ye.decision.tool` 顶层包，**不下沉**到域子包：工具可能跨域复用。
- `AbstractDomainAgent` 模板：构造时接 `ChatModel + ChatMemory + List<ToolCallback>
  + 系统提示 + 域名（路由器识别用）`。

---

## 4. 数据流

### 4.1 单轮请求时序

```
User: "知识库里说工单怎么处理？然后帮我提一个"
  │
  ▼
ChatController.chat(sessionId, msg)
  │
  ▼
AlibabaAgent.chat(sessionId, msg)
  ├─ 1. 读 ChatMemory.get(sessionId) → 历史
  ├─ 2. 构建 AgentContext
  ├─ 3. 调用 RouterAgent.streamAsync(ctx)
  └─ 4. GraphEventAdapter 把 NodeOutput 流转成 Flux<AgentEvent>
  │
  ▼
RouterAgent (LlmRoutingAgent)
  │  系统提示："你是分流器，从 [knowledge/data/workorder/external/chat] 中选"
  │  → 决策: knowledge   ──▶ 推 ROUTE 事件
  ▼
KnowledgeAgent (ReactAgent)
  │  ReAct 循环：
  │    Thought  → 推 THOUGHT
  │    Action(knowledgeSearchTool) → 推 ACTION + 执行 + OBSERVATION
  │    Final Answer → 推 ANSWER
  ▼
回到 RouterAgent → 推 DONE
  │
  └─ AlibabaAgent 写回 ChatMemory.add(sessionId, turnMessages)
```

### 4.2 GraphEventAdapter 映射表

| Graph 输出 | AgentEvent |
|-----------|-----------|
| `LlmRoutingAgent` 决策节点完成 | `ROUTE { targetAgent }` |
| `ReactAgent` 模型输出含 toolCalls 的文本部分 | `THOUGHT { text }` |
| `ReactAgent` ToolCall 节点 | `ACTION { toolName, arguments }` |
| `ReactAgent` ToolResponse 节点 | `OBSERVATION { result }` |
| `ReactAgent` 终止时的 Assistant 文本 | `ANSWER { text, agentName }` |
| Graph 流结束 | `DONE` |
| 任意节点异常 | `ERROR { message, recoverable }` |

### 4.3 中止与并行

- 现有前端 `stopStreaming` (commit `b67a293`) 工作方式不变。
- `ChatController` Flux 取消 → `AlibabaAgent` 在 `Flux.create` 的 cancel hook 取消 Graph 执行。
- 多工具并行执行由 `ReactAgent` 内部支持，无需自己写 `CompletableFuture` 编排。

### 4.4 ChatMemory 写入

- 终态（DONE 或 ERROR）一次性写入完整 turn（user msg + 所有 assistant/tool messages）。
- 共享 `sessionId`，路由器和所有子 Agent 共用一份记忆 → 跨域上下文连续。
- 沿用现有 `RedissonChatMemoryRepository`，窗口大小 `decision.agent.memory-window-size`（默认 20）。

---

## 5. 错误处理

| 场景 | 处理 |
|------|------|
| 路由 LLM 返回不在白名单的 agent 名 | fallback 到 `ChatAgent` |
| 子 Agent 工具调用异常 | 工具层返回 `{"error": "..."}`，LLM 自决是否重试或道歉（沿用现约定） |
| Graph 整体异常 | 推 `ERROR` 事件，前端展示，**不抛断 SSE** |
| 超过最大步数 | `ReactAgent.builder().maxIterations(10)` 限制；触发后推 `ERROR { recoverable=false }` |

---

## 6. 配置项扩展

`bootstrap.yaml` 的 `decision.agent.*` 命名空间扩展：

```yaml
decision:
  agent:
    memory-window-size: 20            # 沿用
    max-iterations: 10                # 沿用 (旧的 MAX_REACT_STEPS)
    router:
      fallback-agent: chat            # 路由器 LLM 给出未知 agentName 时的兜底
```

无新外部依赖（`spring-ai-alibaba-agent-framework 1.1.2.0` 已在 pom 中）。

---

## 7. 扩展性 — 新增域 Agent 的成本

例：新增"订单 Agent"。

1. 新建 `agent/domains/order/OrderAgent.java`（继承 `AbstractDomainAgent`，5–10 行）
2. 新建 `agent/domains/order/OrderPrompts.java`（系统提示常量）
3. `AgentConfig` 加一个 `@Bean orderAgent(ChatModel, ChatMemory, ToolCatalog)`
4. **完毕** — `RouterAgent` 通过 `List<AbstractDomainAgent>` 自动收所有域 Bean，
   路由提示由 `RouterAgentFactory` 从域名+描述自动生成。

零修改其它已有代码。

---

## 8. AgentConfig 装配示例

```java
@Configuration
public class AgentConfig {

    @Bean KnowledgeAgent knowledgeAgent(ChatModel m, ChatMemory mem, ToolCatalog tc) {
        return new KnowledgeAgent(m, mem, tc.byNames("knowledgeSearchTool"));
    }

    @Bean DataAgent dataAgent(ChatModel m, ChatMemory mem, ToolCatalog tc) {
        return new DataAgent(m, mem, tc.byNames(
            "queryRedisTool", "queryMysqlTool",
            "mcpListTables", "mcpDescribeTable", "mcpQueryData", "mcpExecuteSql"));
    }

    @Bean WorkOrderAgent workOrderAgent(ChatModel m, ChatMemory mem, ToolCatalog tc) {
        return new WorkOrderAgent(m, mem, tc.byNames("workOrderTool"));
    }

    @Bean ExternalApiAgent externalApiAgent(ChatModel m, ChatMemory mem, ToolCatalog tc) {
        return new ExternalApiAgent(m, mem, tc.byNames("callExternalApiTool"));
    }

    @Bean ChatAgent chatAgent(ChatModel m, ChatMemory mem) {
        return new ChatAgent(m, mem);
    }

    @Bean RouterAgent routerAgent(List<AbstractDomainAgent> domains, ChatModel m, ChatMemory mem) {
        return RouterAgentFactory.build(m, mem, domains);
    }

    @Bean Agent agent(RouterAgent router, ChatMemory mem) {
        return new AlibabaAgent(router, mem);
    }
}
```

`ToolCatalog` 新增方法：

```java
public List<ToolCallback> byNames(String... names) {
    // 按名字精确筛选；任一名字找不到工具 → 抛 IllegalStateException 让启动失败
}
```

---

## 9. 测试策略

按用户决策：**仅集成测试**，不写 mock 单元测试，避免为测试而过度抽象。

- `decision-app/src/test/.../agent/AlibabaAgentIT.java`
  - `@SpringBootTest`，连真实 DashScope（`@EnabledIfEnvironmentVariable("DASHSCOPE_API_KEY")`）
  - 5 个测试：每个域一个典型问题，断言事件流包含正确的 `ROUTE { agentName=xxx }` + 至少一个 `ANSWER`
  - 1 个跨域上下文测试（"知识库里…" → "那帮我提一个"），断言两轮分别路由到 `knowledge` 和 `workorder`，
    且第二轮答案能引用第一轮信息（验证共享 ChatMemory）

---

## 10. 前端改动 (`decision-web`)

- `src/api/chat.ts`：`AgentEvent` 类型扩展 `ROUTE` 事件
- `src/stores/chat.ts`：新增 route 状态字段，UI 增加"路由到 KnowledgeAgent"提示气泡
- 现有 e2e 测试（`a3d4ad7` 主题切换 + 现有聊天流）跑通即视为前端兼容 OK

---

## 11. 迁移策略

**直接替换**（用户决策）。一次性切换，无 feature flag、无双轨。

操作顺序（实现阶段细化）：

1. 新增 `agent/` 包及全部新代码
2. `ChatController` 改注入 `Agent`（实际为 `AlibabaAgent`）
3. 删除 `service/AgentService.java`、`service/AgentAliService.java`、`service/Agent.java`、`domain/dto/ReActEvent.java`
4. 前端同步更新 `api/chat.ts`、`stores/chat.ts`
5. 跑集成测试 + e2e 测试
6. 联调一次手动 smoke

---

## 12. 风险

| 风险 | 缓解 |
|------|------|
| `LlmRoutingAgent` 路由不准（小语种、模糊问句） | `chat` 兜底 + 集成测试覆盖跨域用例 |
| `ReactAgent` 流式输出节点结构与文档不一致（框架 1.x 还在演进） | 实现阶段先做 spike：跑一次最小 `ReactAgent` 看 `NodeOutput` 结构，再编 `GraphEventAdapter` |
| 多 Agent 共享 ChatMemory 导致 token 膨胀 | 沿用窗口=20，必要时按 sessionId 主动 trim |
| 直接替换无回退路径 | 旧代码在 git 历史中可恢复；上线前用集成测试和手动 smoke 兜底 |

---

## 13. 后续（不在本次范围）

- 路由器加持久化决策日志（审计）
- 子 Agent 间显式 handoff（"我处理不了，转给 X"）
- ParallelAgent 编排"先查知识 + 同时查工单状态"类问题
- 把工单全流程拆成 SequentialAgent 子图（建单 → 通知 → 闭环）
