# Spring AI Alibaba Agent — 设计规范

**日期：** 2026-03-20
**项目：** decision
**状态：** 已批准

---

## 1. 背景与目标

构建一个企业级多场景智能 Agent 服务，覆盖客服查询、运维辅助、通用企业问答三类场景。Agent 通过 ReAct 链式推理自主决策调用哪些工具获取数据，最终以流式方式响应前端。

---

## 2. 技术栈

| 组件 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.3.x |
| AI 框架 | Spring AI Alibaba | 1.0.0.2 |
| 服务注册/配置 | Spring Cloud Alibaba Nacos | 2023.0.x |
| 服务间调用 | Spring Cloud OpenFeign | 4.1.x |
| 会话记忆 | Spring Data Redis (MessageWindowChatMemory) | — |
| 流式响应 | SSE (SseEmitter) | — |
| 构建工具 | Maven | — |
| Java 版本 | 17 | — |

> 注意：现有 `pom.xml` 中 `spring-boot-starter-parent` 版本为 `4.0.4`，实现阶段需替换为 `3.3.x`。

---

## 3. 整体架构

```
前端 (SSE)
    │  POST /api/chat/stream  { sessionId, message }
    ▼
┌─────────────────────────────────────────────┐
│         decision 服务 (Spring Boot 3.3.x)    │
│                                             │
│  ChatController                             │
│   - 创建 SseEmitter，提交异步任务            │
│   - 订阅 AgentService 返回的 Flux<String>   │
│   - 每个 token 推送一条 SSE event           │
│       │                                     │
│  AgentService                               │
│   - 加载/保存 ChatMemory (Redis)            │
│   - 构建 ChatClient 请求（含 Tools）        │
│   - 返回 Flux<String> token 流              │
│       │  ChatClient (Spring AI Alibaba)      │
│       │  Tools 通过 .defaultFunctions()     │
│       │  注册，Spring AI 自动路由            │
│       │                                     │
│  ┌────┴──────────────────────────┐          │
│  │      Tool Registry (3 Tools)  │          │
│  │  query_mysql                  │          │
│  │  query_redis                  │          │
│  │  call_external_api            │          │
│  └────────────────┬──────────────┘          │
└───────────────────┼─────────────────────────┘
                    │ Feign
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
│   ├── AiConfig.java              # @Configuration；构建 ChatClient Bean
│   │                              # 注入 3 个 Tool Bean（.defaultFunctions(...)）
│   │                              # 注入 MessageWindowChatMemory Bean（从 Redis 构建）
│   │                              # @ConfigurationProperties(prefix="decision.agent") + @RefreshScope
│   ├── RedisConfig.java           # Redis 序列化配置（Jackson2JsonRedisSerializer）
│   └── WebConfig.java             # CORS 配置；不负责 SseEmitter 超时（超时在 Controller 构建时设定）
│
├── controller/
│   └── ChatController.java        # POST /api/chat/stream → SseEmitter(timeout=180s)
│                                  # 在异步线程（ThreadPoolTaskExecutor）中订阅 AgentService 返回的 Flux
│                                  # token → sseEmitter.send(token)
│                                  # onComplete → sseEmitter.send("[DONE]") + complete()
│                                  # onError → sseEmitter.send(event:error, JSON) + complete()
│
├── service/
│   └── AgentService.java          # 从 Redis 加载 ChatMemory（key: session:{sessionId}，TTL: 24h）
│                                  # 构建 ChatClient 请求，返回 Flux<String>
│                                  # 在 Flux doOnComplete 回调中异步写回完整消息列表到 Redis
│
├── tool/
│   ├── QueryMysqlTool.java        # @Bean("queryMysqlTool")，implements Function<QueryMysqlReq, String>
│   │                              # Map<String, DownstreamClient> 路由：target → FeignClient 调用
│   ├── QueryRedisTool.java        # @Bean("queryRedisTool")，implements Function<QueryRedisReq, String>
│   └── CallExternalApiTool.java   # @Bean("callExternalApiTool")，implements Function<ApiCallReq, String>
│                                  # 内部使用 RestTemplate 调用外部 HTTP 接口
│
├── feign/
│   ├── OrderServiceClient.java    # @FeignClient(name="order-service")
│   └── UserServiceClient.java     # @FeignClient(name="user-service")
│   (在 DecisionApplication 上添加 @EnableFeignClients(basePackages="com.ye.decision.feign"))
│
├── dto/
│   ├── ChatRequest.java           # { sessionId, message }
│   ├── QueryMysqlReq.java
│   ├── QueryRedisReq.java
│   └── ApiCallReq.java
│
├── common/
│   ├── Result.java                # 统一响应格式 { code, msg, data }
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice，处理非 SSE 路径异常
│                                    # SSE 路径异常在 Controller 异步线程内 catch 处理
│
└── prompt/
    └── system-prompt.md           # System Prompt 模板（classpath 资源）
```

---

## 5. 依赖管理（pom.xml）

通过 BOM 统一管理版本，避免冲突：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.alibaba.cloud.ai</groupId>
      <artifactId>spring-ai-alibaba-bom</artifactId>
      <version>1.0.0.2</version>
      <type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-alibaba-dependencies</artifactId>
      <version>2023.0.3.2</version>
      <type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2023.0.3</version>
      <type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
  </dependency>
  <dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <!-- bootstrap.yaml 支持（Spring Cloud 2020+ 默认禁用 bootstrap context） -->
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
  </dependency>
</dependencies>
```

> 现有 `pom.xml` 中的 `spring-boot-starter-webmvc` 替换为 `spring-boot-starter-web`（同一制品，保持依赖表一致）。

---

## 6. 配置文件结构

项目需要 `bootstrap.yaml`（Nacos Config 在应用启动前加载，早于 `application.yaml`）：

```yaml
# bootstrap.yaml（本地，不含敏感信息）
spring:
  application:
    name: decision
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: dev
        group: DEFAULT_GROUP
        file-extension: yaml
        data-id: decision.yaml   # 远端配置文件名
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev
```

```yaml
# Nacos 中的 decision.yaml（远端，含敏感信息）
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        model: qwen-plus          # 可切换：qwen-max、qwen-turbo 等
  data:
    redis:
      host: 127.0.0.1
      port: 6379

decision:
  agent:
    memory-window-size: 10        # 保留最近 N 轮对话
    max-tool-iterations: 5        # ReAct 最大工具调用轮数，防止无限循环
  sse:
    timeout-ms: 180000            # SseEmitter 超时时间（毫秒）
```

`AiConfig` 使用 `@ConfigurationProperties(prefix = "decision.agent")` + `@RefreshScope`，Nacos 推送配置变更后自动生效，无需重启。

---

## 7. ReAct 数据流

```
1.  前端 POST { sessionId: "abc", message: "查一下用户123的最近订单" }
2.  ChatController 创建 SseEmitter(timeout=180s)，提交任务到 ThreadPoolTaskExecutor 后立即返回
3.  AgentService 从 Redis 加载 key="session:abc" 的历史消息（TTL=24h，不存在则空列表）
4.  构建 MessageWindowChatMemory(windowSize=N) → ChatClient 请求包含 SystemPrompt + 历史 + 当前 message
5.  调用 ChatClient.stream()，Spring AI 开始与 LLM 交互
6.  若 LLM 返回 tool_call → Spring AI 路由到对应 @Bean Tool.apply()（同步执行）
7.  Tool 返回 JSON 字符串 → 追加为 tool message → 再次调用 LLM（ReAct 循环）
8.  循环超过 max-tool-iterations 时，Spring AI 抛出 MaxToolIterationsExceededException
    → AgentService catch 后生成降级提示消息（"已收集部分信息，无法完全回答..."）推入 Flux
9.  LLM 生成最终 text 回答，token 流通过 Flux<String> 返回给 ChatController
10. ChatController 异步线程：token → sseEmitter.send(data:token)
    onComplete → sseEmitter.send(data:[DONE]) + sseEmitter.complete()
    onError（含 SSE 路径所有异常）→ catch 后 sseEmitter.send(event:error, JSON) + complete()
11. AgentService 在 Flux doOnComplete 中异步将完整消息列表写回 Redis（key="session:abc"，TTL=24h）
    写入失败 → log.warn，不影响本次响应
```

---

## 8. Tool 定义

工具通过 `@Bean` 注册，`ChatClient` 调用 `.defaultFunctions("queryMysqlTool", "queryRedisTool", "callExternalApiTool")` 绑定。

每个 Tool Bean 需要 `@Description` 注解（Spring AI 用此作为发送给 LLM 的 function description）。

### 8.1 query_mysql
- **Bean 名称：** `queryMysqlTool`
- **@Description：** `"查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。"`
- **InputSchema：**
  ```java
  record QueryMysqlReq(
      @JsonProperty(required = true)
      String target,   // 下游服务：固定值 "order-service" | "user-service"
      @JsonProperty(required = true)
      String query     // 查询条件，如 "userId=123 最近5条订单"
  ) {}
  ```
- **路由规则：** Tool 内部维护 `Map<String, DownstreamClient> clients`（key = target），每个 DownstreamClient 封装对应 FeignClient。未知 target 直接返回错误 JSON，不抛异常。
- **FeignClient 接口（简要）：**
  - `OrderServiceClient`：`GET /internal/orders?query={query}` → 返回 JSON 数组
  - `UserServiceClient`：`GET /internal/users?query={query}` → 返回 JSON 对象或数组
  - Feign 超时配置：connectTimeout=3s，readTimeout=10s

### 8.2 query_redis
- **Bean 名称：** `queryRedisTool`
- **@Description：** `"查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。"`
- **InputSchema：**
  ```java
  record QueryRedisReq(
      @JsonProperty(required = true)
      String keyPattern,   // 完整 Redis key，如 "user:123:profile"
      @JsonProperty(required = true)
      String dataType      // "string" | "hash" | "zset" | "list"
  ) {}
  ```
- **输出格式：** `{"key": "...", "type": "...", "value": <原始值>}`
  - string → `"value": "字符串内容"`
  - hash → `"value": { "field": "val", ... }`
  - zset/list → `"value": ["item1", "item2", ...]`
- **key 不存在时：** 返回 `{"key": "...", "type": "...", "value": null, "found": false}`

### 8.3 call_external_api
- **Bean 名称：** `callExternalApiTool`
- **@Description：** `"调用外部第三方服务，包括天气查询、汇率查询、物流追踪。"`
- **InputSchema：**
  ```java
  record ApiCallReq(
      @JsonProperty(required = true)
      String service,  // 固定值："weather" | "logistics" | "exchange-rate"
      @JsonProperty(required = true)
      String params    // JSON 字符串，各 service 的参数结构见下
  ) {}
  ```
- **实现：** 内部使用 `RestTemplate`，各 service 对应一个适配方法，URL 通过 Nacos 配置注入。
- **params 结构（LLM 调用时应按此构造）：**
  - `weather`：`{"city": "北京"}` → 返回 `{"city":"...","temp":"...","desc":"..."}`
  - `logistics`：`{"trackingNo": "SF1234567"}` → 返回 `{"status":"...","location":"..."}`
  - `exchange-rate`：`{"from": "USD", "to": "CNY"}` → 返回 `{"rate": 7.25}`
- **未知 service：** 返回 `{"error": "unknown_service", "message": "不支持的外部服务: {service}"}`

### 8.4 Tool 错误输出统一格式

所有 Tool 在异常时返回（不抛异常，避免中断 ReAct 循环）：
```json
{ "error": "<error_code>", "message": "<可读描述>", "tool": "<tool_name>" }
```
LLM 收到含 `"error"` 字段的响应时，在最终回答中说明查询失败及原因。

---

## 9. System Prompt

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
- 如果工具返回错误（包含 "error" 字段），告知用户该查询失败及原因
- 回答简洁、结构化，必要时使用列表或表格
- 如果无法回答，明确告知用户并说明原因
```

---

## 10. 统一响应格式

```java
// 非流式接口统一使用
public record Result<T>(int code, String msg, T data) {
    public static <T> Result<T> ok(T data) { return new Result<>(200, "success", data); }
    public static <T> Result<T> error(String msg) { return new Result<>(500, msg, null); }
}
```

**SSE 事件格式：**
```
// 正常 token
data: 这是

data: 一段

data: 流式回答

// 结束信号
data: [DONE]

// 错误事件（event 字段区分类型）
event: error
data: {"code":500,"msg":"LLM调用超时","data":null}
```

---

## 11. 错误处理

| 场景 | 处理方式 |
|------|---------|
| LLM 调用超时/失败（非 SSE 路径） | `GlobalExceptionHandler`（`@RestControllerAdvice`）捕获，返回 `Result.error()` |
| LLM 调用超时/失败（SSE 路径） | Controller 异步线程内 catch，推送 `event:error data:{"code":500,"msg":"..."}` 后 `complete()` |
| Tool 调用下游服务失败（Feign 异常） | Tool 内 try-catch，返回统一错误 JSON（见 8.4），不向上抛异常，LLM 据此回答用户 |
| Tool 收到未知 target / service | Tool 内直接返回 `unknown_service` 错误 JSON，不抛异常 |
| ReAct 超过 max-tool-iterations | Spring AI 抛出 `MaxToolIterationsExceededException`，AgentService catch 后向 Flux 发送降级提示 |
| sessionId 不存在 | Redis key 缺失时 ChatMemory 返回空列表，自动开启新会话，无需报错 |
| Redis 写回失败 | `doOnComplete` 中 catch，记录 `log.warn`，不影响本次 SSE 响应；下次该 session 历史为空 |
| 模型配置缺失/非法 | `@ConfigurationProperties` 绑定 + `@Validated` + `@NotBlank`（apiKey 等必填项）；校验失败则拒绝启动 |
| Feign 调用超时 | 统一配置 connectTimeout=3s，readTimeout=10s；超时后 Feign 抛异常，Tool 内 catch 转为错误 JSON |
