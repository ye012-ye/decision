# Spring AI Alibaba Agent — 设计规范

**日期：** 2026-03-20
**项目：** decision
**状态：** 已批准

---

## 1. 背景与目标

构建一个企业级多场景智能 Agent 服务，覆盖客服查询、运维辅助、通用企业问答三类场景。Agent 通过 ReAct 链式推理自主决策调用哪些工具获取数据，最终以流式方式响应前端。

---

## 2. 技术栈

| 组件 | 选型 |
|------|------|
| 框架 | Spring Boot 3.3.x |
| AI 框架 | Spring AI Alibaba (ChatClient + FunctionCallback) |
| 服务注册/配置 | Spring Cloud Alibaba Nacos |
| 服务间调用 | Spring Cloud OpenFeign |
| 会话记忆 | Spring Data Redis (MessageWindowChatMemory) |
| 流式响应 | SSE (SseEmitter) |
| 构建工具 | Maven |
| Java 版本 | 17 |

---

## 3. 整体架构

```
前端 (SSE)
    │  POST /api/chat/stream  { sessionId, message }
    ▼
┌─────────────────────────────────────────────┐
│         decision 服务 (Spring Boot 3.3.x)    │
│                                             │
│  ChatController (SseEmitter)                │
│       │                                     │
│  AgentService                               │
│       │  ChatClient (Spring AI Alibaba)      │
│       │       │                             │
│       │  ChatMemory (Redis, 滑动窗口)        │
│       │                                     │
│  ┌────┴──────────────────────────┐          │
│  │      Tool Registry (3 Tools)  │          │
│  │  query_mysql                  │          │
│  │  query_redis                  │          │
│  │  call_external_api            │          │
│  └────────────────┬──────────────┘          │
└───────────────────┼─────────────────────────┘
                    │ Feign / RestClient
          ┌─────────┼──────────┐
          ▼         ▼          ▼
      用户服务   订单服务   外部API...
      (MySQL)   (MySQL)
```

---

## 4. 模块结构

```
com.ye.decision
├── config/
│   ├── AiConfig.java              # ChatClient Bean，注入 Tools + Memory，读 Nacos 配置
│   ├── RedisConfig.java           # Redis 序列化配置
│   └── WebConfig.java             # CORS、SSE 超时配置
│
├── controller/
│   └── ChatController.java        # POST /api/chat/stream → SseEmitter
│
├── service/
│   └── AgentService.java          # 组装 prompt + memory，调用 ChatClient.stream()
│
├── tool/
│   ├── QueryMysqlTool.java        # Function<QueryMysqlReq, String>
│   ├── QueryRedisTool.java        # Function<QueryRedisReq, String>
│   └── CallExternalApiTool.java   # Function<ApiCallReq, String>
│
├── dto/
│   ├── ChatRequest.java           # { sessionId, message }
│   ├── QueryMysqlReq.java
│   ├── QueryRedisReq.java
│   └── ApiCallReq.java
│
├── common/
│   ├── Result.java                # 统一响应格式 { code, msg, data }
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice 统一异常处理
│
└── prompt/
    └── system-prompt.md           # System Prompt 模板（classpath 资源）
```

---

## 5. 核心依赖（pom.xml）

```xml
<!-- Spring AI Alibaba -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
</dependency>

<!-- Nacos 配置中心 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>

<!-- Nacos 服务注册 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- OpenFeign 服务间调用 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<!-- Redis（ChatMemory） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Web（SSE） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## 6. ReAct 数据流

```
1. 前端 POST { sessionId: "abc", message: "查一下用户123的最近订单" }
2. ChatController 创建 SseEmitter，异步执行
3. AgentService 从 Redis 加载该 session 的历史消息（滑动窗口）
4. 拼装 SystemPrompt + 历史消息 + 当前 message → 发给 LLM
5. LLM 返回 tool_call → Spring AI 自动路由到对应 Tool.apply()
6. Tool 通过 Feign 调用下游服务，返回 JSON 字符串
7. 结果作为 tool message 追加上下文，再次发给 LLM（ReAct 循环）
8. LLM 生成最终回答，流式 token 通过 SseEmitter 推送前端
9. 对话历史写回 Redis（保留最近 N 轮，N 由 Nacos 配置）
```

---

## 7. Tool 定义

### 7.1 query_mysql
- **描述：** 查询结构化业务数据，如订单、用户信息、交易记录、统计报表
- **InputSchema：**
  ```java
  record QueryMysqlReq(
      String target,  // 目标服务，如 "order-service"、"user-service"
      String query    // 查询条件描述
  ) {}
  ```

### 7.2 query_redis
- **描述：** 查询缓存数据、热点数据、实时计数器、会话信息、排行榜
- **InputSchema：**
  ```java
  record QueryRedisReq(
      String keyPattern,  // Redis key 模式，如 "user:123:session"
      String dataType     // "string" | "hash" | "zset" | "list"
  ) {}
  ```

### 7.3 call_external_api
- **描述：** 查询外部第三方服务，如天气、汇率、物流追踪、地图服务等
- **InputSchema：**
  ```java
  record ApiCallReq(
      String service,  // 服务标识，如 "weather"、"logistics"、"exchange-rate"
      String params    // JSON 格式请求参数
  ) {}
  ```

所有 Tool 返回值统一为 **String（JSON）**，由 LLM 自行解析并组织最终回答。

---

## 8. System Prompt

```
你是一个企业智能助手，服务于客服、运维和通用业务查询场景。

## 可用工具
- query_mysql：查询结构化业务数据，如订单、用户信息、交易记录、统计报表
- query_redis：查询缓存数据、热点数据、实时计数器、会话信息、排行榜
- call_external_api：查询外部第三方服务，如天气、汇率、物流追踪

## 决策优先级
1. 优先查询 Redis（低延迟，热点数据）
2. 其次查询 MySQL（精确结构化数据）
3. 最后调用外部 API（网络开销大，按需使用）

## 行为规范
- 工具调用前，先判断哪个工具最合适
- 如果一次工具调用结果不完整，可以继续调用其他工具补充
- 最终回答必须基于工具返回的真实数据，不得编造
- 回答简洁、结构化，必要时使用列表或表格
- 如果无法回答，明确告知用户并说明原因
```

---

## 9. 统一响应格式

```java
// 非流式接口统一使用
public record Result<T>(int code, String msg, T data) {
    public static <T> Result<T> ok(T data) { return new Result<>(200, "success", data); }
    public static <T> Result<T> error(String msg) { return new Result<>(500, msg, null); }
}
```

SSE 流式接口直接推送 token 字符串，最后一条事件发送 `[DONE]` 信号。

---

## 10. Nacos 配置项

```yaml
# Nacos 中的 decision.yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        model: qwen-plus   # 可切换：qwen-max、qwen-turbo 等

decision:
  agent:
    memory-window-size: 10  # 保留最近 N 轮对话
```

---

## 11. 错误处理

| 场景 | 处理方式 |
|------|---------|
| LLM 调用超时/失败 | GlobalExceptionHandler 捕获，返回 `Result.error()`；SSE 推送 error event |
| Tool 调用下游服务失败 | Tool 内捕获异常，返回 JSON 错误描述，LLM 根据错误描述回答用户 |
| sessionId 不存在 | 自动创建新会话，从空历史开始 |
| 模型配置缺失 | 启动时 `@Value` 校验，Nacos 配置刷新后自动生效 |
