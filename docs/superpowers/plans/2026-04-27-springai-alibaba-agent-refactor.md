# Spring AI Alibaba Agent Framework 重构 —— 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Spring AI Alibaba Agent Framework (1.1.2.0) 把现有手写 ReAct 循环重构为"路由 + 5 个域专家子 Agent"多智能体拓扑，删除旧实现。

**Architecture:** `ChatController → AlibabaAgent → LlmRoutingAgent → {Knowledge, Data, WorkOrder, External, Chat} ReactAgent`，所有 Agent 共享一份 `ChatMemory`（按 sessionId）。框架 `Flux<NodeOutput>` 流由 `GraphEventAdapter` 翻译为项目 `AgentEvent` 流。

**Tech Stack:** Spring Boot 3.3 / Spring AI 1.1.2 / Spring AI Alibaba 1.1.2.0 (`spring-ai-alibaba-agent-framework`, `spring-ai-alibaba-starter-dashscope`) / Reactor / Vue 3 + TS。

**Spec:** [`docs/superpowers/specs/2026-04-27-springai-alibaba-agent-refactor-design.md`](../specs/2026-04-27-springai-alibaba-agent-refactor-design.md)

---

## 关键约定（所有任务都遵循）

- **包路径**：所有新代码放在 `com.ye.decision.agent.*`
- **Bean 装配**：域 Agent **不写** `@Component`/`@Service`，统一在 `agent/config/AgentConfig` 用 `@Bean` 装配
- **构造器注入**，禁止字段注入
- **不写单元测试**（仅集成测试，按 spec §9）
- **每个任务结束 commit**，commit message 用 `feat(agent): ...` / `refactor(agent): ...` / `chore(agent): ...`
- **import 风格**：按当前项目惯例，不引入 Lombok（项目本来没有 Lombok）

---

## Task 1: Spike — 验证 ReactAgent + LlmRoutingAgent + NodeOutput 实际形状

**为什么需要**：spec §12 已登记此风险——框架 `NodeOutput` 的具体 subclass 在文档中描述模糊，必须先跑通最小例子，肉眼观察输出，再据此设计 `GraphEventAdapter`。

**Files:**
- Create: `decision-app/src/test/java/com/ye/decision/agent/spike/AgentFrameworkSpike.java`

- [ ] **Step 1: 写 spike 测试**

```java
package com.ye.decision.agent.spike;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Spike：跑通最小 ReactAgent + LlmRoutingAgent，把 NodeOutput 流的真实结构打印出来，
 * 用于设计 GraphEventAdapter 映射规则。一次性测试，验证后保留作为参考。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class AgentFrameworkSpike {

    @Autowired
    private ChatModel chatModel;

    @Test
    void exploreNodeOutputShape() throws Exception {
        ReactAgent leaf = ReactAgent.builder()
            .name("echo")
            .description("Echoes the user message back.")
            .model(chatModel)
            .instruction("You are a friendly echo bot. Reply briefly in Chinese.")
            .build();

        LlmRoutingAgent router = LlmRoutingAgent.builder()
            .name("root-router")
            .description("Routes user messages to the right specialist.")
            .model(chatModel)
            .subAgents(List.of(leaf))
            .fallbackAgent("echo")
            .build();

        Flux<NodeOutput> stream = router.stream(List.of(new UserMessage("你好，简单介绍一下你自己")));
        stream.doOnNext(n -> System.out.println(
                "=== NodeOutput class=" + n.getClass().getName()
                        + " node=" + n.node()
                        + " state=" + n.state()))
              .blockLast();
    }
}
```

- [ ] **Step 2: 跑 spike**

Run: `./mvnw -pl decision-app -Dtest=AgentFrameworkSpike test`
Expected: 通过（测试只在环境变量 `DASHSCOPE_API_KEY` 存在时运行）。控制台打印若干 `=== NodeOutput class=...` 行。

- [ ] **Step 3: 记录关键观察**

把控制台中出现的所有 `NodeOutput class=…/node=…/state=…` 截屏或贴到文件 `decision-app/src/test/java/com/ye/decision/agent/spike/NODE_OUTPUT_NOTES.md`，重点记下：
- 路由决策出现在哪个 `node`、`state` 里以什么 key 携带（用于 `ROUTE` 事件映射）
- `ReactAgent` 的工具调用出现在哪个 `node`、`state` 里 `messages` 的形态（用于 `THOUGHT/ACTION/OBSERVATION/ANSWER` 映射）

```markdown
# NodeOutput 形状观察记录（spike 输出）

## 路由节点
- node = "..."
- state.messages 末尾形如 ...

## ReactAgent 节点
- node = "..."
- state.messages 末尾形如 ...
```

- [ ] **Step 4: Commit**

```bash
git add decision-app/src/test/java/com/ye/decision/agent/spike/
git commit -m "chore(agent): spike — capture NodeOutput shape for adapter design"
```

---

## Task 2: 创建 core 包基础类型

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/core/Agent.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/core/AgentEventType.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/core/AgentEvent.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/core/AgentContext.java`

> 不动旧 `service/Agent.java` 和 `domain/dto/ReActEvent.java` —— Task 11 才删除（避免编译断裂）。新接口与旧接口同名但不同包，可以共存。

- [ ] **Step 1: 写 Agent 接口**

`agent/core/Agent.java`:

```java
package com.ye.decision.agent.core;

import reactor.core.publisher.Flux;

/**
 * Agent 顶层抽象：接收会话 ID + 用户消息，返回流式事件。
 */
public interface Agent {

    /**
     * @param sessionId 会话标识，用于读写共享 ChatMemory
     * @param message   用户输入
     * @return 流式 AgentEvent（thought/action/observation/answer/route/done/error）
     */
    Flux<AgentEvent> chat(String sessionId, String message);
}
```

- [ ] **Step 2: 写事件类型枚举**

`agent/core/AgentEventType.java`:

```java
package com.ye.decision.agent.core;

/**
 * Agent 流事件类型。覆盖框架 NodeOutput → 项目事件的全部映射目标。
 */
public enum AgentEventType {
    /** 路由器决策：选中目标子 Agent */
    ROUTE,
    /** 模型推理（含工具调用前的 thinking 文本） */
    THOUGHT,
    /** 模型发起的工具调用 */
    ACTION,
    /** 工具返回结果 */
    OBSERVATION,
    /** 最终回答（可能多个分片） */
    ANSWER,
    /** 流结束 */
    DONE,
    /** 异常 */
    ERROR
}
```

- [ ] **Step 3: 写 AgentEvent record**

`agent/core/AgentEvent.java`:

```java
package com.ye.decision.agent.core;

/**
 * Agent 流事件。content 字段语义按 type 不同：
 * <ul>
 *   <li>ROUTE       — content = 目标子 agent 的 name</li>
 *   <li>THOUGHT     — content = 推理文本</li>
 *   <li>ACTION      — content = "{toolName} | {jsonArguments}"（沿用旧约定，方便前端解析）</li>
 *   <li>OBSERVATION — content = 工具原始返回（通常是 JSON）</li>
 *   <li>ANSWER      — content = 最终回答文本（多次推送时按顺序拼接）</li>
 *   <li>DONE        — content = "[DONE]"</li>
 *   <li>ERROR       — content = JSON: {"code":..., "msg":...}</li>
 * </ul>
 */
public record AgentEvent(AgentEventType type, String content) {

    public static AgentEvent route(String agentName) {
        return new AgentEvent(AgentEventType.ROUTE, agentName);
    }
    public static AgentEvent thought(String text) {
        return new AgentEvent(AgentEventType.THOUGHT, text);
    }
    public static AgentEvent action(String toolName, String arguments) {
        return new AgentEvent(AgentEventType.ACTION, toolName + " | " + arguments);
    }
    public static AgentEvent observation(String result) {
        return new AgentEvent(AgentEventType.OBSERVATION, result);
    }
    public static AgentEvent answer(String text) {
        return new AgentEvent(AgentEventType.ANSWER, text);
    }
    public static AgentEvent done() {
        return new AgentEvent(AgentEventType.DONE, "[DONE]");
    }
    public static AgentEvent error(String message) {
        String safe = message == null ? "" : message.replace("\"", "\\\"");
        return new AgentEvent(AgentEventType.ERROR, "{\"code\":500,\"msg\":\"" + safe + "\"}");
    }
}
```

- [ ] **Step 4: 写 AgentContext record**

`agent/core/AgentContext.java`:

```java
package com.ye.decision.agent.core;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 单轮请求的运行时上下文。AlibabaAgent 在调用 RouterAgent 前组装。
 */
public record AgentContext(String sessionId, String userMessage, List<Message> history) {
    public AgentContext {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/core/
git commit -m "feat(agent): add core abstractions — Agent / AgentEvent / AgentContext"
```

---

## Task 3: AbstractDomainAgent 基类

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/core/AbstractDomainAgent.java`

**职责**：把"域 Agent = name + description + ReactAgent" 的装配封装成模板。子类只声明域名、描述、系统提示和工具集，构造时拼装内部 `ReactAgent`。`getReactAgent()` 暴露给 `LlmRoutingAgent.subAgents()` 使用（框架要求 `List<com.alibaba.cloud.ai.graph.agent.Agent>`）。

- [ ] **Step 1: 写 AbstractDomainAgent**

```java
package com.ye.decision.agent.core;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Objects;

/**
 * 域专家 Agent 模板。每个业务域继承本类，声明：
 * <ul>
 *   <li>{@link #name()} — 唯一域名（路由器据此识别）</li>
 *   <li>{@link #description()} — 域职责一句话描述（路由器据此选择）</li>
 *   <li>{@link #systemPrompt()} — 域专属系统提示</li>
 * </ul>
 * 工具集由构造时传入；ReactAgent 由本基类装配，子类不直接接触框架细节。
 */
public abstract class AbstractDomainAgent {

    private final ReactAgent reactAgent;

    protected AbstractDomainAgent(ChatModel chatModel,
                                  List<ToolCallback> tools,
                                  int maxIterations) {
        Objects.requireNonNull(chatModel, "chatModel");
        this.reactAgent = buildReactAgent(chatModel, tools == null ? List.of() : tools, maxIterations);
    }

    /** 域名，全局唯一，路由器据此 dispatch。例如 "knowledge"/"data"/"workorder"。 */
    public abstract String name();

    /** 一句话描述域职责，路由器把所有子 agent 的 description 拼成路由提示。 */
    public abstract String description();

    /** 域专属系统提示。 */
    protected abstract String systemPrompt();

    /** 暴露内部 ReactAgent，用于注册到 LlmRoutingAgent 的 subAgents。 */
    public ReactAgent getReactAgent() {
        return reactAgent;
    }

    private ReactAgent buildReactAgent(ChatModel chatModel,
                                       List<ToolCallback> tools,
                                       int maxIterations) {
        try {
            ReactAgent.Builder builder = ReactAgent.builder()
                .name(name())
                .description(description())
                .model(chatModel)
                .instruction(systemPrompt())
                .parallelToolExecution(true);
            if (!tools.isEmpty()) {
                builder.tools(tools);
            }
            // maxIterations 在 1.1.2.0 框架里通过 hooks/interceptor 控制；
            // 这里先把上限信息写进 prompt 让模型自我约束，并依赖框架默认上限兜底。
            // 若 spike 中发现 builder 暴露了上限 API，则替换为该 API。
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build ReactAgent for domain " + name(), e);
        }
    }
}
```

> **Note for executor**：`Builder` 类是 `com.alibaba.cloud.ai.graph.agent.Builder`（抽象类，`ReactAgent.builder()` 静态方法返回它）。如果 IDE 报无法 import，把类型显式写为 `com.alibaba.cloud.ai.graph.agent.Builder`。

- [ ] **Step 2: 编译验证**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/core/AbstractDomainAgent.java
git commit -m "feat(agent): add AbstractDomainAgent base — wraps ReactAgent assembly"
```

---

## Task 4: 5 个域 Agent + 系统提示

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/knowledge/KnowledgePrompts.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/knowledge/KnowledgeAgent.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/data/DataPrompts.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/data/DataAgent.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/workorder/WorkOrderPrompts.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/workorder/WorkOrderAgent.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/external/ExternalPrompts.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/external/ExternalApiAgent.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/chat/ChatPrompts.java`
- Create: `decision-app/src/main/java/com/ye/decision/agent/domains/chat/ChatAgent.java`

> 5 个域结构相同，只是 prompt 和 name/description 不同。下面给出 `KnowledgeAgent` 完整代码，其它 4 个按相同模式建。

- [ ] **Step 1: 写 KnowledgePrompts**

```java
package com.ye.decision.agent.domains.knowledge;

final class KnowledgePrompts {
    private KnowledgePrompts() {}

    static final String SYSTEM = """
        你是企业知识库专家，专注于在内部产品文档/FAQ/政策规范中检索答案。
        遇到知识类问题，先用 knowledgeSearchTool 检索，引用原文回答；
        若工具返回为空或与问题无关，直接坦白没有找到，不要臆测。
        回答用中文，简洁、有引用。
        """;

    static final String DESCRIPTION =
        "处理企业内部知识库相关问题：产品文档、操作手册、FAQ、政策规范、技术文档检索。";
}
```

- [ ] **Step 2: 写 KnowledgeAgent**

```java
package com.ye.decision.agent.domains.knowledge;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class KnowledgeAgent extends AbstractDomainAgent {

    public static final String NAME = "knowledge";

    public KnowledgeAgent(ChatModel chatModel, List<ToolCallback> tools, int maxIterations) {
        super(chatModel, tools, maxIterations);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return KnowledgePrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return KnowledgePrompts.SYSTEM; }
}
```

- [ ] **Step 3: 写 DataPrompts + DataAgent**

```java
package com.ye.decision.agent.domains.data;

final class DataPrompts {
    private DataPrompts() {}

    static final String SYSTEM = """
        你是数据查询专家，能访问 MySQL（结构化业务数据）和 Redis（缓存/热点数据），
        以及通过 MCP 暴露的数据库元数据/SQL 执行工具。
        用户问数据时按以下顺序判断：
          1. 缓存/会话/排行榜 → queryRedisTool
          2. 业务表精确查询 → queryMysqlTool
          3. 不熟悉表结构 → 先 mcpListTables / mcpDescribeTable，再 mcpQueryData
          4. 写操作（极少数）→ mcpExecuteSql，并明确告知用户操作内容
        始终用 SQL/字段名做精确表达，不臆造表名。
        """;

    static final String DESCRIPTION =
        "处理数据查询/统计/报表/SQL 类问题，覆盖 MySQL、Redis 缓存和数据库元数据查询。";
}
```

```java
package com.ye.decision.agent.domains.data;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class DataAgent extends AbstractDomainAgent {

    public static final String NAME = "data";

    public DataAgent(ChatModel chatModel, List<ToolCallback> tools, int maxIterations) {
        super(chatModel, tools, maxIterations);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DataPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return DataPrompts.SYSTEM; }
}
```

- [ ] **Step 4: 写 WorkOrderPrompts + WorkOrderAgent**

```java
package com.ye.decision.agent.domains.workorder;

final class WorkOrderPrompts {
    private WorkOrderPrompts() {}

    static final String SYSTEM = """
        你是工单管理助手。所有工单生命周期操作必须通过 workOrderTool：
          - create: 收集 type/title/description/customerId 后创建
          - query : 按工单号或客户查询
          - update: 推进状态（PENDING→PROCESSING→RESOLVED 等）
          - close : 关闭并要求填写解决方案
        创建前若信息不全，主动问用户补齐；不要凭空填字段。
        回答用中文，工单号原样返回。
        """;

    static final String DESCRIPTION =
        "处理客服工单全生命周期：创建、查询、状态更新、关闭。涉及投诉/报修/申请/反馈类请求。";
}
```

```java
package com.ye.decision.agent.domains.workorder;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class WorkOrderAgent extends AbstractDomainAgent {

    public static final String NAME = "workorder";

    public WorkOrderAgent(ChatModel chatModel, List<ToolCallback> tools, int maxIterations) {
        super(chatModel, tools, maxIterations);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return WorkOrderPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return WorkOrderPrompts.SYSTEM; }
}
```

- [ ] **Step 5: 写 ExternalPrompts + ExternalApiAgent**

```java
package com.ye.decision.agent.domains.external;

final class ExternalPrompts {
    private ExternalPrompts() {}

    static final String SYSTEM = """
        你是外部信息助手，通过 callExternalApiTool 访问第三方服务：
          - weather       : 天气查询
          - logistics     : 物流追踪
          - exchange-rate : 汇率
        根据用户问题选择对应 service，必要参数填齐再调用。
        若调用失败，把错误信息原样告诉用户，不要假装查到。
        """;

    static final String DESCRIPTION =
        "处理需要外部第三方服务的问题：天气、物流追踪、汇率查询。";
}
```

```java
package com.ye.decision.agent.domains.external;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class ExternalApiAgent extends AbstractDomainAgent {

    public static final String NAME = "external";

    public ExternalApiAgent(ChatModel chatModel, List<ToolCallback> tools, int maxIterations) {
        super(chatModel, tools, maxIterations);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return ExternalPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return ExternalPrompts.SYSTEM; }
}
```

- [ ] **Step 6: 写 ChatPrompts + ChatAgent**

```java
package com.ye.decision.agent.domains.chat;

final class ChatPrompts {
    private ChatPrompts() {}

    static final String SYSTEM = """
        你是友好的客服助理兜底。当用户的问题不属于知识库/数据/工单/外部 API 时，
        用中文自然地与用户对话；遇到超出能力的请求，礼貌说明并引导用户重新表述。
        不要承诺自己没有的能力，不要捏造数据。
        """;

    static final String DESCRIPTION =
        "通用闲聊/问候/无明确意图时的兜底，处理无需任何工具的对话。";
}
```

```java
package com.ye.decision.agent.domains.chat;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public class ChatAgent extends AbstractDomainAgent {

    public static final String NAME = "chat";

    public ChatAgent(ChatModel chatModel, int maxIterations) {
        super(chatModel, List.of(), maxIterations);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return ChatPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return ChatPrompts.SYSTEM; }
}
```

- [ ] **Step 7: 编译验证**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/domains/
git commit -m "feat(agent): add 5 domain agents — knowledge / data / workorder / external / chat"
```

---

## Task 5: 扩展 ToolCatalog —— 增加 byNames

**Files:**
- Modify: `decision-app/src/main/java/com/ye/decision/service/ToolCatalog.java`

- [ ] **Step 1: 改为非函数式接口，新增 byNames**

```java
package com.ye.decision.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供当前可用工具快照。
 *
 * @author ye
 */
public interface ToolCatalog {

    /** 返回当前所有工具（含本地 + MCP 远端动态发现）。 */
    List<ToolCallback> getToolCallbacks();

    /**
     * 按名字精确筛选工具。任一名字找不到 → 抛 {@link IllegalStateException}
     * （让启动 / Bean 装配阶段失败，而不是运行时悄悄丢工具）。
     */
    default List<ToolCallback> byNames(String... names) {
        if (names == null || names.length == 0) {
            return List.of();
        }
        Map<String, ToolCallback> index = new LinkedHashMap<>();
        for (ToolCallback cb : getToolCallbacks()) {
            index.put(cb.getToolDefinition().name(), cb);
        }
        List<ToolCallback> selected = new java.util.ArrayList<>(names.length);
        for (String name : names) {
            ToolCallback cb = index.get(name);
            if (cb == null) {
                throw new IllegalStateException("Tool not found in catalog: " + name
                    + " (available: " + index.keySet() + ")");
            }
            selected.add(cb);
        }
        return List.copyOf(selected);
    }
}
```

> **Note**：旧接口标了 `@FunctionalInterface`，但新增 `default` 方法不破坏 SAM 形态（只有一个抽象方法仍是 `getToolCallbacks`）。`AiConfig.toolCatalog(...)` 中的 lambda 仍可工作。把 `@FunctionalInterface` 注解保留与否都可以；这里直接移除以避免误导。

- [ ] **Step 2: 编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS（`AiConfig.java` 中的 `() -> ...` lambda 仍然兼容）

- [ ] **Step 3: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/service/ToolCatalog.java
git commit -m "feat(agent): ToolCatalog.byNames(String...) for explicit per-domain tool selection"
```

---

## Task 6: GraphEventAdapter —— NodeOutput 流翻译

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/stream/GraphEventAdapter.java`

**前置依赖**：Task 1 spike 已记录 `NodeOutput` 形状。本任务的实现按观察到的 state/messages 结构编写映射。

- [ ] **Step 1: 写适配器**

```java
package com.ye.decision.agent.stream;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.ye.decision.agent.core.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把 Spring AI Alibaba Graph 的 {@link NodeOutput} 流翻译成项目 {@link AgentEvent} 流。
 *
 * <h2>映射规则</h2>
 * <ul>
 *   <li>state.messages 末尾出现新 {@link AssistantMessage}（含 toolCalls）→ THOUGHT (text) + ACTION* (每个 toolCall)</li>
 *   <li>state.messages 末尾出现新 {@link ToolResponseMessage} → OBSERVATION* (每个 toolResponse)</li>
 *   <li>state.messages 末尾出现新 {@link AssistantMessage}（无 toolCalls，文本非空）→ ANSWER</li>
 *   <li>node 名包含 "routing"/"router" 且 state 中含目标 agent 名 → ROUTE</li>
 * </ul>
 *
 * <p><b>实现策略</b>：流式重放 message 列表；用 message identity (System#identityHashCode)
 * 去重，确保同一条 message 只产生一次事件——这是因为 Graph 在多个 NodeOutput 中
 * 会重复携带累积的 message 列表。</p>
 *
 * <p>路由识别：spike 中观察到的路由 node 名（默认匹配 "routing"/"router" 子串），
 * 以及决策 key（默认尝试从 state 读取 "next_agent" / "selected_agent" / "agent_name"，
 * 取首个非空值）。如 spike 显示其它 key，按观察值修改 {@link #ROUTING_STATE_KEYS}。</p>
 */
public final class GraphEventAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphEventAdapter.class);

    private static final List<String> ROUTING_STATE_KEYS =
        List.of("next_agent", "selected_agent", "agent_name", "route");

    private GraphEventAdapter() {}

    public static Flux<AgentEvent> toEvents(Flux<NodeOutput> nodeOutputs) {
        Set<Integer> seenMessageIds = new HashSet<>();
        Set<String> emittedRoutes = new HashSet<>();
        return nodeOutputs.flatMap(node -> Flux.fromIterable(translate(node, seenMessageIds, emittedRoutes)))
            .onErrorResume(err -> {
                log.error("Graph stream failed", err);
                return Flux.just(AgentEvent.error(err.getMessage()));
            })
            .concatWith(Flux.just(AgentEvent.done()));
    }

    private static List<AgentEvent> translate(NodeOutput node,
                                              Set<Integer> seen,
                                              Set<String> emittedRoutes) {
        List<AgentEvent> events = new java.util.ArrayList<>();

        // ROUTE
        String nodeName = node.node() == null ? "" : node.node().toLowerCase();
        if (nodeName.contains("routing") || nodeName.contains("router")) {
            String target = readRoutingTarget(node);
            if (target != null && emittedRoutes.add(target)) {
                events.add(AgentEvent.route(target));
            }
        }

        // 消息流
        Object msgs = node.state() == null ? null : node.state().value("messages").orElse(null);
        if (!(msgs instanceof List<?> list)) {
            return events;
        }

        for (Object item : list) {
            if (!(item instanceof Message message)) {
                continue;
            }
            int id = System.identityHashCode(message);
            if (!seen.add(id)) {
                continue;
            }
            switch (message) {
                case AssistantMessage assistant -> {
                    String text = assistant.getText();
                    if (assistant.hasToolCalls()) {
                        if (text != null && !text.isBlank()) {
                            events.add(AgentEvent.thought(text));
                        }
                        for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
                            events.add(AgentEvent.action(tc.name(), tc.arguments()));
                        }
                    } else if (text != null && !text.isBlank()) {
                        events.add(AgentEvent.answer(text));
                    }
                }
                case ToolResponseMessage tr -> {
                    for (ToolResponseMessage.ToolResponse resp : tr.getResponses()) {
                        events.add(AgentEvent.observation(resp.responseData()));
                    }
                }
                default -> { /* SystemMessage / UserMessage 不输出事件 */ }
            }
        }
        return events;
    }

    private static String readRoutingTarget(NodeOutput node) {
        if (node.state() == null) {
            return null;
        }
        for (String key : ROUTING_STATE_KEYS) {
            Object v = node.state().value(key).orElse(null);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
```

> **Important for executor**：上面 `ROUTING_STATE_KEYS` 是猜测列表。**完成后跑 Task 13 集成测试**——若 ROUTE 事件不出现，回到 Task 1 的 `NODE_OUTPUT_NOTES.md` 看真实 key，更新这个常量列表。这是 spec §12 风险的具体兜底点。

- [ ] **Step 2: 编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/stream/GraphEventAdapter.java
git commit -m "feat(agent): GraphEventAdapter — translate NodeOutput stream to AgentEvent"
```

---

## Task 7: RouterAgentFactory —— 装配根 LlmRoutingAgent

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/router/RouterAgentFactory.java`

- [ ] **Step 1: 写工厂**

```java
package com.ye.decision.agent.router;

import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用所有 {@link AbstractDomainAgent} 子 Agent 构造一个 {@link LlmRoutingAgent}。
 * 路由系统提示由各子 agent 的 name + description 自动拼装，新增域时零修改本工厂。
 */
public final class RouterAgentFactory {

    private RouterAgentFactory() {}

    public static LlmRoutingAgent build(ChatModel chatModel,
                                        List<AbstractDomainAgent> domains,
                                        String fallbackAgentName) {
        if (domains == null || domains.isEmpty()) {
            throw new IllegalArgumentException("Router needs at least one sub-agent");
        }
        String routingPrompt = buildRoutingPrompt(domains);
        try {
            return LlmRoutingAgent.builder()
                .name("root-router")
                .description("根路由器：根据用户问题选择最合适的域专家 Agent。")
                .model(chatModel)
                .systemPrompt(routingPrompt)
                .fallbackAgent(fallbackAgentName)
                .subAgents(domains.stream()
                    .map(d -> (com.alibaba.cloud.ai.graph.agent.Agent) d.getReactAgent())
                    .collect(Collectors.toList()))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build root LlmRoutingAgent", e);
        }
    }

    private static String buildRoutingPrompt(List<AbstractDomainAgent> domains) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是分流器。读取用户最新消息（结合历史），从下列子 Agent 中选最合适的一个：\n");
        for (AbstractDomainAgent d : domains) {
            sb.append("  - ").append(d.name()).append(" : ").append(d.description()).append('\n');
        }
        sb.append("""

            选择规则：
              1. 优先精确匹配领域；
              2. 多个领域均可时，选用户问题里最具体的那个；
              3. 都不沾边时，选 chat 兜底；
              4. 只输出选中的 agent 名（小写），不要任何额外解释。
            """);
        return sb.toString();
    }
}
```

- [ ] **Step 2: 编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/router/RouterAgentFactory.java
git commit -m "feat(agent): RouterAgentFactory — auto-build LlmRoutingAgent from domains"
```

---

## Task 8: AlibabaAgent —— Agent 接口的实现

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/AlibabaAgent.java`

**职责**：实现项目 `Agent` 接口；读历史 → 调路由器 stream → 适配为 `AgentEvent` 流 → 终态写回 ChatMemory。

- [ ] **Step 1: 写 AlibabaAgent**

```java
package com.ye.decision.agent;

import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentEvent;
import com.ye.decision.agent.stream.GraphEventAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring AI Alibaba Agent Framework 的 {@link Agent} 唯一实现。
 *
 * 流程：读 ChatMemory → 拼 messages → router.stream → 适配为 AgentEvent → 终态写回 ChatMemory。
 */
public class AlibabaAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AlibabaAgent.class);

    private final LlmRoutingAgent rootRouter;
    private final ChatMemory chatMemory;

    public AlibabaAgent(LlmRoutingAgent rootRouter, ChatMemory chatMemory) {
        this.rootRouter = rootRouter;
        this.chatMemory = chatMemory;
    }

    @Override
    public Flux<AgentEvent> chat(String sessionId, String message) {
        List<Message> history = safeHistory(sessionId);
        UserMessage userMessage = new UserMessage(message);

        List<Message> input = new ArrayList<>(history.size() + 1);
        input.addAll(history);
        input.add(userMessage);

        // 累积本轮模型输出，终态时写回 ChatMemory（与旧 AgentService 行为一致）
        List<Message> turn = new ArrayList<>();
        turn.add(userMessage);

        Flux<com.alibaba.cloud.ai.graph.NodeOutput> raw;
        try {
            raw = rootRouter.stream(input);
        } catch (Exception e) {
            log.error("Failed to start agent stream, sessionId={}", sessionId, e);
            return Flux.just(AgentEvent.error(e.getMessage()), AgentEvent.done());
        }

        Flux<com.alibaba.cloud.ai.graph.NodeOutput> withCapture = raw.doOnNext(node -> captureMessages(node, turn));
        return GraphEventAdapter.toEvents(withCapture)
            .doOnComplete(() -> persistTurn(sessionId, turn))
            .doOnError(err -> log.error("Agent stream error, sessionId={}", sessionId, err));
    }

    private List<Message> safeHistory(String sessionId) {
        try {
            return chatMemory.get(sessionId);
        } catch (Exception e) {
            log.warn("Failed to load chat memory, sessionId={}, falling back to empty", sessionId, e);
            return List.of();
        }
    }

    private void captureMessages(com.alibaba.cloud.ai.graph.NodeOutput node, List<Message> turn) {
        if (node.state() == null) return;
        Object msgs = node.state().value("messages").orElse(null);
        if (!(msgs instanceof List<?> list)) return;
        for (Object item : list) {
            if (item instanceof AssistantMessage am && !turn.contains(am)) {
                turn.add(am);
            } else if (item instanceof org.springframework.ai.chat.messages.ToolResponseMessage trm
                && !turn.contains(trm)) {
                turn.add(trm);
            }
        }
    }

    private void persistTurn(String sessionId, List<Message> turn) {
        try {
            chatMemory.add(sessionId, turn);
        } catch (Exception e) {
            log.warn("Failed to persist turn to ChatMemory, sessionId={}", sessionId, e);
        }
    }
}
```

- [ ] **Step 2: 编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/AlibabaAgent.java
git commit -m "feat(agent): AlibabaAgent — entry impl wiring router stream to AgentEvent"
```

---

## Task 9: AgentConfig —— Bean 装配

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/agent/config/AgentConfig.java`

- [ ] **Step 1: 写 AgentConfig**

```java
package com.ye.decision.agent.config;

import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.AlibabaAgent;
import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AbstractDomainAgent;
import com.ye.decision.agent.domains.chat.ChatAgent;
import com.ye.decision.agent.domains.data.DataAgent;
import com.ye.decision.agent.domains.external.ExternalApiAgent;
import com.ye.decision.agent.domains.knowledge.KnowledgeAgent;
import com.ye.decision.agent.domains.workorder.WorkOrderAgent;
import com.ye.decision.agent.router.RouterAgentFactory;
import com.ye.decision.service.ToolCatalog;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentConfig {

    @Value("${decision.agent.max-iterations:10}")
    private int maxIterations;

    @Value("${decision.agent.router.fallback-agent:chat}")
    private String fallbackAgent;

    @Bean
    public KnowledgeAgent knowledgeAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new KnowledgeAgent(chatModel, catalog.byNames("knowledgeSearchTool"), maxIterations);
    }

    @Bean
    public DataAgent dataAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new DataAgent(chatModel, catalog.byNames(
            "queryRedisTool", "queryMysqlTool",
            "mcpListTables", "mcpDescribeTable", "mcpQueryData", "mcpExecuteSql"
        ), maxIterations);
    }

    @Bean
    public WorkOrderAgent workOrderAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new WorkOrderAgent(chatModel, catalog.byNames("workOrderTool"), maxIterations);
    }

    @Bean
    public ExternalApiAgent externalApiAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new ExternalApiAgent(chatModel, catalog.byNames("callExternalApiTool"), maxIterations);
    }

    @Bean
    public ChatAgent chatAgent(ChatModel chatModel) {
        return new ChatAgent(chatModel, maxIterations);
    }

    @Bean
    public LlmRoutingAgent rootRouter(ChatModel chatModel, List<AbstractDomainAgent> domains) {
        return RouterAgentFactory.build(chatModel, domains, fallbackAgent);
    }

    @Bean
    public Agent agent(LlmRoutingAgent rootRouter, ChatMemory chatMemory) {
        return new AlibabaAgent(rootRouter, chatMemory);
    }
}
```

> **Note**：`List<AbstractDomainAgent> domains` 的注入由 Spring 自动收集所有 `AbstractDomainAgent` 类型的 Bean。MCP 工具（`mcpListTables` 等）若 MCP server 未启动，`ToolCatalog.byNames` 会抛 `IllegalStateException`——这是有意为之，因为 `decision-app` 启动顺序明确要求先启 `decision-mcp-server`（CLAUDE.md 已记录）。

- [ ] **Step 2: 添加配置项到 bootstrap.yaml**

Modify: `decision-app/src/main/resources/bootstrap.yaml`，找到 `decision.agent:` 段（旧值）扩展为：

```yaml
decision:
  agent:
    memory-window-size: 20
    max-iterations: 10
    router:
      fallback-agent: chat
```

> 若 `decision.agent.memory-window-size` 已存在，仅追加 `max-iterations` 和 `router.fallback-agent` 两项即可。

- [ ] **Step 3: 编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/agent/config/AgentConfig.java decision-app/src/main/resources/bootstrap.yaml
git commit -m "feat(agent): AgentConfig — wire 5 domains + router + AlibabaAgent beans"
```

---

## Task 10: 切换 ChatController 到新 Agent + 序列化新事件

**Files:**
- Modify: `decision-app/src/main/java/com/ye/decision/controller/ChatController.java`

**改动点**：
1. 注入从 `AgentService` 改为 `com.ye.decision.agent.core.Agent`
2. 事件序列化：旧的 `event.type()` 是 String，新的 `event.type()` 是 `AgentEventType` 枚举——需要转小写字符串
3. 兼容前端：`done` / `error` 事件由适配器内部产生，controller 不再额外发 `done`

- [ ] **Step 1: 重写 ChatController**

```java
package com.ye.decision.controller;

import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentEventType;
import com.ye.decision.domain.dto.ChatRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;

/**
 * SSE 流式聊天接口。事件名（lower-case）对应前端监听器：
 * route / thought / action / observation / answer / done / error。
 *
 * @author ye
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 180_000L;
    private static final MediaType UTF8_TEXT =
        new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final Agent agent;
    private final ExecutorService sseExecutor;

    public ChatController(Agent agent, ExecutorService sseExecutor) {
        this.agent = agent;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        sseExecutor.execute(() -> {
            try {
                agent.chat(request.sessionId(), request.message())
                    .doOnNext(event -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name(toEventName(event.type()))
                                .data(event.content(), UTF8_TEXT));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(emitter::complete)
                    .doOnError(e -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"code\":500,\"msg\":\"" + e.getMessage() + "\"}", UTF8_TEXT));
                            emitter.complete();
                        } catch (Exception ex) {
                            emitter.completeWithError(ex);
                        }
                    })
                    .blockLast();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private static String toEventName(AgentEventType type) {
        return type.name().toLowerCase();
    }
}
```

- [ ] **Step 2: 编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD FAILURE — 旧 `service.AgentService` / `service.AgentAliService` / `domain.dto.ReActEvent` 仍引用 `Agent`/`ReActEvent`，但 controller 已切换。这是预期的中间状态，下一个 Task 11 删除这些。

- [ ] **Step 3: 不 commit，直接进入 Task 11**

---

## Task 11: 清理旧实现

**Files:**
- Delete: `decision-app/src/main/java/com/ye/decision/service/AgentService.java`
- Delete: `decision-app/src/main/java/com/ye/decision/service/AgentAliService.java`
- Delete: `decision-app/src/main/java/com/ye/decision/service/Agent.java`
- Delete: `decision-app/src/main/java/com/ye/decision/domain/dto/ReActEvent.java`

- [ ] **Step 1: 删除旧文件**

```bash
git rm decision-app/src/main/java/com/ye/decision/service/AgentService.java
git rm decision-app/src/main/java/com/ye/decision/service/AgentAliService.java
git rm decision-app/src/main/java/com/ye/decision/service/Agent.java
git rm decision-app/src/main/java/com/ye/decision/domain/dto/ReActEvent.java
```

- [ ] **Step 2: 全工程编译**

Run: `./mvnw -pl decision-app -am compile`
Expected: BUILD SUCCESS

若失败：搜索剩余对 `service.Agent` / `service.AgentService` / `ReActEvent` 的引用，通常只在 `AiConfig` 之外没有。修正引用，导入 `com.ye.decision.agent.core.Agent`。

```bash
# 帮助命令
grep -rn "com.ye.decision.service.Agent" decision-app/src/main/java
grep -rn "com.ye.decision.domain.dto.ReActEvent" decision-app/src/main/java
```

- [ ] **Step 3: Commit Task 10 + 11 一起**

```bash
git add decision-app/src/main/java/com/ye/decision/controller/ChatController.java
git commit -m "refactor(agent): ChatController uses new Agent + delete legacy AgentService/ReActEvent"
```

---

## Task 12: 前端 —— 扩展 ChatEventType 并支持 ROUTE 事件

**Files:**
- Modify: `decision-web/src/types/chat.ts`
- Modify: `decision-web/src/stores/workspace.ts`

- [ ] **Step 1: 扩展 ChatEventType + 加 RouteProcessEntry**

Replace `decision-web/src/types/chat.ts` content:

```typescript
export type ChatEventType =
  | 'route'
  | 'thought'
  | 'action'
  | 'observation'
  | 'answer'
  | 'done'
  | 'error';

export interface ChatStreamEvent {
  event: ChatEventType;
  data: string;
}

export type ChatProcessType = Extract<
  ChatEventType,
  'route' | 'thought' | 'action' | 'observation'
>;

export interface ChatProcessEntry {
  id: string;
  type: ChatProcessType;
  content: string;
}

export type AssistantMessageStatus = 'streaming' | 'done' | 'error';

export interface ChatUserMessage {
  id: string;
  role: 'user';
  content: string;
}

export interface ChatAssistantMessage {
  id: string;
  role: 'assistant';
  content: string;
  status: AssistantMessageStatus;
  process: ChatProcessEntry[];
  processExpanded: boolean;
  /** 路由器选中的子 agent 名（最近一次 route 事件的 data） */
  routedAgent?: string;
}

export type ChatMessage = ChatUserMessage | ChatAssistantMessage;

export interface ChatRequest {
  sessionId: string;
  message: string;
}
```

- [ ] **Step 2: 更新 workspace store 的事件分支**

Modify `decision-web/src/stores/workspace.ts` —— 在 `streamChat` 回调里把 `route` 加入分支处理。找到 callback 中的 `if (event.event === 'answer') { ... }` 块（行 126-148），替换整段为：

```typescript
            withAssistantMessage((target) => {
              if (event.event === 'answer') {
                target.content += event.data;
                updateTicketContextFromText(target.content);
              } else if (event.event === 'route') {
                target.routedAgent = event.data;
                appendProcessEntry(target, 'route', event.data);
              } else if (
                event.event === 'thought' ||
                event.event === 'action' ||
                event.event === 'observation'
              ) {
                appendProcessEntry(target, event.event, event.data);
              } else if (event.event === 'done') {
                if (target.status === 'streaming') {
                  target.status = 'done';
                }
              } else if (event.event === 'error') {
                target.status = 'error';
                target.processExpanded = true;
                const errorText = event.data.trim() || FALLBACK_ASSISTANT_ERROR_MESSAGE;
                if (!target.content.trim()) {
                  target.content = errorText;
                } else {
                  target.content += `\n${errorText}`;
                }
              }
            });
```

- [ ] **Step 3: 在 ChatAssistantMessage 工厂处初始化 routedAgent**

仍在 `decision-web/src/stores/workspace.ts`，找到 `const assistantMessage: ChatAssistantMessage = { ... }` 块（约行 86-93），加一个 `routedAgent: undefined,`：

```typescript
      const assistantMessage: ChatAssistantMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '',
        status: 'streaming',
        process: [],
        processExpanded: false,
        routedAgent: undefined,
      };
```

- [ ] **Step 4: 类型检查 + 单测**

Run:
```bash
cd decision-web
npm run build
npm run test
```
Expected:
- `npm run build` 通过 vue-tsc 检查
- `npm run test` 通过现有 `workspace.spec.ts`（不需要改测试，因为 store 行为对已有事件类型保持兼容）

如果 `workspace.spec.ts` 因为 store 多了 `routedAgent` 字段断言失败，按测试文件提示更新断言。

- [ ] **Step 5: Commit**

```bash
git add decision-web/src/types/chat.ts decision-web/src/stores/workspace.ts
git commit -m "feat(web): handle route event from agent stream — show routed agent"
```

---

## Task 13: 集成测试 —— 5 个域 + 1 个跨域

**Files:**
- Create: `decision-app/src/test/java/com/ye/decision/agent/AlibabaAgentIT.java`

**前置依赖**：
- 环境变量 `DASHSCOPE_API_KEY` 已设置
- 本地 MySQL/Redis/Nacos/RabbitMQ/Milvus 已起（参 CLAUDE.md "外部依赖"）
- `decision-mcp-server` 已起（监听 8081）—— 否则 MCP 工具装配会失败

> 因为这些外部依赖较重，整个测试类用 `@EnabledIfEnvironmentVariable("DASHSCOPE_API_KEY")` 守门，CI 中默认跳过。

- [ ] **Step 1: 写 IT**

```java
package com.ye.decision.agent;

import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentEvent;
import com.ye.decision.agent.core.AgentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class AlibabaAgentIT {

    @Autowired
    private Agent agent;

    private List<AgentEvent> run(String session, String message) {
        return agent.chat(session, message).collectList().block();
    }

    private void assertRoutedTo(List<AgentEvent> events, String expectedAgent) {
        assertThat(events)
            .filteredOn(e -> e.type() == AgentEventType.ROUTE)
            .extracting(AgentEvent::content)
            .contains(expectedAgent);
    }

    private void assertHasAnswer(List<AgentEvent> events) {
        assertThat(events)
            .filteredOn(e -> e.type() == AgentEventType.ANSWER)
            .isNotEmpty();
    }

    @Test
    void knowledgeDomain() {
        var events = run(UUID.randomUUID().toString(),
            "请在知识库里查一下我们公司的退换货政策。");
        assertRoutedTo(events, "knowledge");
        assertHasAnswer(events);
    }

    @Test
    void dataDomain() {
        var events = run(UUID.randomUUID().toString(),
            "查询数据库里订单表有多少条记录。");
        assertRoutedTo(events, "data");
        assertHasAnswer(events);
    }

    @Test
    void workOrderDomain() {
        var events = run(UUID.randomUUID().toString(),
            "帮我提一个工单：customerId=C001，物流问题，订单 SO123 还没收到。");
        assertRoutedTo(events, "workorder");
        assertHasAnswer(events);
    }

    @Test
    void externalDomain() {
        var events = run(UUID.randomUUID().toString(),
            "查一下今天北京的天气。");
        assertRoutedTo(events, "external");
        assertHasAnswer(events);
    }

    @Test
    void chatFallback() {
        var events = run(UUID.randomUUID().toString(), "你好，介绍一下你自己。");
        assertRoutedTo(events, "chat");
        assertHasAnswer(events);
    }

    @Test
    void crossDomainContext_sharedMemory() {
        String session = UUID.randomUUID().toString();
        var first = run(session, "知识库里说工单流程是什么？");
        assertRoutedTo(first, "knowledge");

        var second = run(session, "好的，那帮我按这个流程提一个工单。");
        assertRoutedTo(second, "workorder");
        assertHasAnswer(second);
    }
}
```

- [ ] **Step 2: 跑测试**

Run: `./mvnw -pl decision-app -Dtest=AlibabaAgentIT test`
Expected: 6 个测试全过。

- [ ] **Step 3: 若 ROUTE 断言失败**

按 Task 6 末尾的注释——回到 `NODE_OUTPUT_NOTES.md` 看 spike 记录的真实 routing key，改 `GraphEventAdapter.ROUTING_STATE_KEYS`，重跑。

- [ ] **Step 4: 若 ANSWER 断言失败**

打印事件流（`events.forEach(System.out::println)`）观察实际事件类型。常见原因：
- `ReactAgent` 的最终 `AssistantMessage` 在 `state.messages` 中没出现新元素 → 检查 `GraphEventAdapter` 的 message 去重逻辑
- 路由结束节点的 `AssistantMessage` 没透传到外层 stream → 在 spike 记录中确认 final node 名

- [ ] **Step 5: Commit**

```bash
git add decision-app/src/test/java/com/ye/decision/agent/AlibabaAgentIT.java
git commit -m "test(agent): integration tests — 5 domains + cross-domain shared memory"
```

---

## Task 14: 前端 e2e 烟测

**Files:**
- 不创建新文件，跑现有 e2e 套件

- [ ] **Step 1: 起后端**

终端 A:
```bash
./mvnw -pl decision-mcp-server -am spring-boot:run
```
终端 B（待 mcp 起来后）:
```bash
./mvnw -pl decision-app -am spring-boot:run
```

- [ ] **Step 2: 跑前端 e2e**

终端 C:
```bash
cd decision-web
npm run test:e2e
```
Expected: 现有 e2e 测试（包括 `a3d4ad7` 的 theme toggle 测试和 chat 相关测试）全通过。

如果 chat 相关 e2e 因 SSE 行为变化失败，更新对应 e2e 文件中对事件类型的断言。

- [ ] **Step 3: 手动 smoke**

打开 `http://localhost:5173`（或 vite 实际端口），在 chat 输入框分别试：
- "你好" → UI 应显示 "已路由到 chat"，回复正常
- "查一下北京天气" → UI 应显示 "已路由到 external"
- "帮我提一个物流工单" → UI 应显示 "已路由到 workorder"

把过程中暴露的 UI 问题（比如 `process` 区域 route 没渲染好）作为 follow-up 修复，不阻塞主线 commit。

- [ ] **Step 4: 若有 UI 调整 → commit**

```bash
git add decision-web/...
git commit -m "feat(web): show routed agent badge in chat process panel"
```

---

## Task 15: 收尾 —— 更新 CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 更新 "Agent loop" 章节**

打开 `CLAUDE.md`，找到 `### Agent loop (decision-app)` 一节（描述了"hand-rolled ReAct loop"），整段替换为：

```markdown
### Agent topology (`decision-app`)

The core is `agent.AlibabaAgent` — a Spring AI Alibaba Agent Framework composition:
**LlmRoutingAgent (root) → 5 domain ReactAgents** (knowledge / data / workorder /
external / chat). All sub-agents share one `ChatMemory` keyed by `sessionId`.

Streaming events fed to the SSE controller (`controller.ChatController`):
- `route` — router decision (which sub-agent picked)
- `thought` — model reasoning
- `action` — tool invocation (`toolName | arguments`)
- `observation` — tool result
- `answer` — final answer chunk
- `done` / `error`

Bounded by `decision.agent.max-iterations` (default 10). Memory uses the custom
`RedissonChatMemoryRepository` with window size `decision.agent.memory-window-size`.

Sub-agent assembly lives in `agent/config/AgentConfig`. Adding a new domain =
new subpackage under `agent/domains/<x>/` extending `AbstractDomainAgent` +
one `@Bean` line in `AgentConfig` — the router auto-collects all
`AbstractDomainAgent` beans.

Tools are still aggregated by `service.ToolCatalog`; each domain agent declares
the tool names it needs via `catalog.byNames(...)` at bean construction time.
```

并删除整个 `### Tool selection (important, non-obvious)` 章节（该机制已被路由器取代）。

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude.md): update agent topology section after refactor"
```

---

## 自审 — 计划与 spec 的覆盖检查

| spec 章节 | 对应 task |
|-----------|-----------|
| §2.1 核心组件 | T2 (core) / T3 (AbstractDomainAgent) / T4 (5 domains) / T6 (adapter) / T7 (router) / T8 (AlibabaAgent) |
| §2.2 解耦原则 | T4 + T9（按域装配 + ToolCatalog.byNames，无 TOOL_KEYWORDS） |
| §3 包结构 | T2/T3/T4/T6/T7/T8/T9 文件路径完全匹配 |
| §3.1 不直接 @Component | T4（域 agent 类无 Spring 注解）+ T9（统一 @Bean） |
| §4.1 单轮请求时序 | T8 实现 + T13 测试 |
| §4.2 GraphEventAdapter 映射表 | T6 |
| §4.3 中止与并行 | `parallelToolExecution(true)` 在 T3；中止由 ChatController 的 SSE timeout 兜底（无需新代码） |
| §4.4 ChatMemory 写入 | T8 `persistTurn` |
| §5 错误处理 | T6 (`onErrorResume → ERROR + DONE`) + T8 (`safeHistory`) + T10 (controller error branch) |
| §6 配置项 | T9 Step 2 |
| §7 扩展性 | T15 中 CLAUDE.md 文档化 |
| §9 测试 | T13 |
| §10 前端 | T12 + T14 |
| §11 迁移策略 | T10 + T11 删除旧实现 |
| §12 风险（NodeOutput 形状） | T1 spike + T6 注释 + T13 fallback 路径 |

**覆盖完整。**

## Self-Review fixes

- T6 提到的 `node.state().value(key)` —— 实际 API 在 `OverAllState` 上，按 spike 记录确认；如形状不同，spike 笔记应已揭示。
- T11 删除 `domain/dto/ReActEvent.java` 后，确认无前端字符串字面量依赖（前端用的是 `event.event` 字段，不依赖后端 record 名）。
- T9 中 `bootstrap.yaml` 加配置——确认现有 `decision.agent.memory-window-size` 不要重复添加。
- T12 store 改动是基于现有行 86-93/126-148 的 patch，executor 应先 `Read` 当前文件再 patch（行号会变）。
