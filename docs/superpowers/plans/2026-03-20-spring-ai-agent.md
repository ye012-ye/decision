# Spring AI Alibaba Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个基于 Spring AI Alibaba 的企业级多场景 ReAct Agent，支持 MySQL/Redis/外部API 三类工具，通过 SSE 流式响应前端，会话历史存 Redis，模型配置由 Nacos 动态管理。

**Architecture:** ChatController 接收 SSE 请求，AgentService 用 ChatClient（Spring AI Alibaba）驱动 ReAct 循环，3 个 Tool 通过 @Bean 工厂方法注册（@Description 放在 @Bean 方法上），FeignClient / RestTemplate 调用下游服务，MessageWindowChatMemory 由 RedisChatMemoryRepository 自动持久化。

**Tech Stack:** Spring Boot 3.3.5, Java 21, Spring AI Alibaba 1.0.0.2, Spring Cloud Alibaba 2023.0.3.2, OpenFeign, Spring Data Redis, SseEmitter + 虚拟线程, Maven

---

## 文件清单

| 文件路径 | 操作 | 职责 |
|---------|------|------|
| `pom.xml` | 修改 | Spring Boot 3.3.5 / Java 21，3 BOM + 8 依赖 |
| `src/main/resources/bootstrap.yaml` | 新建 | Nacos 连接配置 |
| `src/main/resources/application.yaml` | 修改 | 仅保留 app name |
| `src/main/resources/prompt/system-prompt.md` | 新建 | System Prompt 模板 |
| `src/main/java/com/ye/decision/common/Result.java` | 新建 | 统一响应格式 |
| `src/main/java/com/ye/decision/common/GlobalExceptionHandler.java` | 新建 | 非 SSE 路径异常处理 |
| `src/main/java/com/ye/decision/dto/ChatRequest.java` | 新建 | SSE 入参 |
| `src/main/java/com/ye/decision/dto/QueryMysqlReq.java` | 新建 | query_mysql 入参 |
| `src/main/java/com/ye/decision/dto/QueryRedisReq.java` | 新建 | query_redis 入参 |
| `src/main/java/com/ye/decision/dto/ApiCallReq.java` | 新建 | call_external_api 入参 |
| `src/main/java/com/ye/decision/feign/DownstreamClient.java` | 新建 | 下游服务统一接口 |
| `src/main/java/com/ye/decision/feign/OrderServiceClient.java` | 新建 | @FeignClient("order-service") |
| `src/main/java/com/ye/decision/feign/UserServiceClient.java` | 新建 | @FeignClient("user-service") |
| `src/main/java/com/ye/decision/tool/QueryRedisTool.java` | 新建 | Redis 查询逻辑（Plain class，无 @Component） |
| `src/main/java/com/ye/decision/tool/QueryMysqlTool.java` | 新建 | MySQL 路由逻辑（Plain class） |
| `src/main/java/com/ye/decision/tool/CallExternalApiTool.java` | 新建 | 外部 API 调用逻辑（Plain class） |
| `src/main/java/com/ye/decision/config/RedisConfig.java` | 新建 | Redis Jackson 序列化 |
| `src/main/java/com/ye/decision/config/WebConfig.java` | 新建 | CORS + RestTemplate Bean |
| `src/main/java/com/ye/decision/config/AiConfig.java` | 新建 | ChatClient + ChatMemory + @Bean Tool 注册（@Description 在此） |
| `src/main/java/com/ye/decision/config/ToolConfig.java` | 新建 | Tool @Bean 工厂（含 @Description），Map<String,DownstreamClient> 路由 |
| `src/main/java/com/ye/decision/service/AgentService.java` | 新建 | ChatClient.stream() 封装，返回 Flux<String> |
| `src/main/java/com/ye/decision/controller/ChatController.java` | 新建 | SSE 端点，虚拟线程 |
| `src/main/java/com/ye/decision/DecisionApplication.java` | 修改 | 添加 @EnableFeignClients |
| `src/test/java/com/ye/decision/common/ResultTest.java` | 新建 | Result 单元测试 |
| `src/test/java/com/ye/decision/common/GlobalExceptionHandlerTest.java` | 新建 | 异常处理 MockMvc 测试 |
| `src/test/java/com/ye/decision/tool/QueryRedisToolTest.java` | 新建 | QueryRedisTool 单元测试 |
| `src/test/java/com/ye/decision/tool/QueryMysqlToolTest.java` | 新建 | QueryMysqlTool 单元测试 |
| `src/test/java/com/ye/decision/tool/CallExternalApiToolTest.java` | 新建 | CallExternalApiTool 单元测试 |
| `src/test/java/com/ye/decision/service/AgentServiceTest.java` | 新建 | AgentService 单元测试 |
| `src/test/java/com/ye/decision/controller/ChatControllerTest.java` | 新建 | ChatController SSE 测试 |

---

## Task 1: 更新 pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 全量替换 pom.xml**

关键变化：java 17→21，新增 `spring-ai-starter-model-chat-memory-repository-redis` 依赖。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.ye</groupId>
    <artifactId>decision</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>decision</name>
    <description>Enterprise AI Agent Service</description>

    <properties>
        <java.version>21</java.version>
    </properties>

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
        <!-- Web / SSE -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring AI Alibaba (ChatClient + FunctionCallback) -->
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

        <!-- OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- Redis（RedisTemplate，供 QueryRedisTool 使用） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Bootstrap Context (bootstrap.yaml) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 验证依赖解析**

```bash
cd D:/coode/decision
./mvnw dependency:resolve -q 2>&1 | tail -5
```

期望：`BUILD SUCCESS`。若 `spring-ai-starter-model-chat-memory-repository-redis` 找不到，说明 BOM 版本未包含该 artifact，改为 `spring-ai-model-chat-memory-repository-redis`（无 starter 前缀）重试。

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: Spring Boot 3.3.5 / Java 21, add AI Alibaba BOMs + Redis ChatMemory dep"
```

---

## Task 2: 配置文件与 System Prompt

**Files:**
- Create: `src/main/resources/bootstrap.yaml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/resources/prompt/system-prompt.md`

- [ ] **Step 1: 创建 bootstrap.yaml**

```yaml
# src/main/resources/bootstrap.yaml
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
        data-id: decision.yaml
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev
```

- [ ] **Step 2: 更新 application.yaml**

```yaml
# src/main/resources/application.yaml
spring:
  application:
    name: decision
```

- [ ] **Step 3: 创建 system-prompt.md**

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

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/
git commit -m "feat: add bootstrap.yaml, system-prompt, update application.yaml"
```

---

## Task 3: Common 层（Result + GlobalExceptionHandler）

**Files:**
- Create: `src/main/java/com/ye/decision/common/Result.java`
- Create: `src/main/java/com/ye/decision/common/GlobalExceptionHandler.java`
- Create: `src/test/java/com/ye/decision/common/ResultTest.java`
- Create: `src/test/java/com/ye/decision/common/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 写 ResultTest**

```java
// src/test/java/com/ye/decision/common/ResultTest.java
package com.ye.decision.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultTest {

    @Test
    void ok_setsCode200AndData() {
        Result<String> r = Result.ok("hello");
        assertThat(r.code()).isEqualTo(200);
        assertThat(r.msg()).isEqualTo("success");
        assertThat(r.data()).isEqualTo("hello");
    }

    @Test
    void error_setsCode500AndNullData() {
        Result<Object> r = Result.error("something went wrong");
        assertThat(r.code()).isEqualTo(500);
        assertThat(r.msg()).isEqualTo("something went wrong");
        assertThat(r.data()).isNull();
    }
}
```

- [ ] **Step 2: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=ResultTest -q 2>&1 | tail -10
```

期望：编译失败（类不存在）。

- [ ] **Step 3: 实现 Result.java**

```java
// src/main/java/com/ye/decision/common/Result.java
package com.ye.decision.common;

public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }
}
```

- [ ] **Step 4: 运行测试确认 PASS**

```bash
./mvnw test -Dtest=ResultTest -q 2>&1 | tail -5
```

期望：`BUILD SUCCESS`

- [ ] **Step 5: 写 GlobalExceptionHandlerTest**

```java
// src/test/java/com/ye/decision/common/GlobalExceptionHandlerTest.java
package com.ye.decision.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalExceptionHandlerTest.FakeController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @RestController
    static class FakeController {
        @GetMapping("/fake-error")
        String boom() { throw new RuntimeException("test error"); }
    }

    @Test
    void runtimeException_returns500WithResultBody() throws Exception {
        mockMvc.perform(get("/fake-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("test error"));
    }
}
```

- [ ] **Step 6: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=GlobalExceptionHandlerTest -q 2>&1 | tail -10
```

- [ ] **Step 7: 实现 GlobalExceptionHandler.java**

```java
// src/main/java/com/ye/decision/common/GlobalExceptionHandler.java
package com.ye.decision.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        return Result.error(e.getMessage());
    }
}
```

- [ ] **Step 8: 运行测试确认 PASS**

```bash
./mvnw test -Dtest="ResultTest,GlobalExceptionHandlerTest" -q 2>&1 | tail -5
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/ye/decision/common/ src/test/java/com/ye/decision/common/
git commit -m "feat: add Result and GlobalExceptionHandler with tests"
```

---

## Task 4: DTO 层

**Files:**
- Create: `src/main/java/com/ye/decision/dto/ChatRequest.java`
- Create: `src/main/java/com/ye/decision/dto/QueryMysqlReq.java`
- Create: `src/main/java/com/ye/decision/dto/QueryRedisReq.java`
- Create: `src/main/java/com/ye/decision/dto/ApiCallReq.java`

- [ ] **Step 1: 创建全部 DTO**

```java
// src/main/java/com/ye/decision/dto/ChatRequest.java
package com.ye.decision.dto;
public record ChatRequest(String sessionId, String message) {}
```

```java
// src/main/java/com/ye/decision/dto/QueryMysqlReq.java
package com.ye.decision.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record QueryMysqlReq(
    @JsonProperty(required = true) String target,   // "order-service" | "user-service"
    @JsonProperty(required = true) String query
) {}
```

```java
// src/main/java/com/ye/decision/dto/QueryRedisReq.java
package com.ye.decision.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record QueryRedisReq(
    @JsonProperty(required = true) String keyPattern,
    @JsonProperty(required = true) String dataType   // "string" | "hash" | "zset" | "list"
) {}
```

```java
// src/main/java/com/ye/decision/dto/ApiCallReq.java
package com.ye.decision.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record ApiCallReq(
    @JsonProperty(required = true) String service,  // "weather" | "logistics" | "exchange-rate"
    @JsonProperty(required = true) String params    // JSON 字符串
) {}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw compile -q 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ye/decision/dto/
git commit -m "feat: add DTO records"
```

---

## Task 5: Feign 层

**Files:**
- Create: `src/main/java/com/ye/decision/feign/DownstreamClient.java`
- Create: `src/main/java/com/ye/decision/feign/OrderServiceClient.java`
- Create: `src/main/java/com/ye/decision/feign/UserServiceClient.java`
- Modify: `src/main/java/com/ye/decision/DecisionApplication.java`

- [ ] **Step 1: 创建 DownstreamClient 接口**

```java
// src/main/java/com/ye/decision/feign/DownstreamClient.java
package com.ye.decision.feign;

public interface DownstreamClient {
    String query(String query);
}
```

- [ ] **Step 2: 创建 FeignClient 实现**

```java
// src/main/java/com/ye/decision/feign/OrderServiceClient.java
package com.ye.decision.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service")
public interface OrderServiceClient extends DownstreamClient {
    @GetMapping("/internal/orders")
    @Override
    String query(@RequestParam("query") String query);
}
```

```java
// src/main/java/com/ye/decision/feign/UserServiceClient.java
package com.ye.decision.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserServiceClient extends DownstreamClient {
    @GetMapping("/internal/users")
    @Override
    String query(@RequestParam("query") String query);
}
```

- [ ] **Step 3: 添加 @EnableFeignClients**

```java
// src/main/java/com/ye/decision/DecisionApplication.java
package com.ye.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.ye.decision.feign")
public class DecisionApplication {
    public static void main(String[] args) {
        SpringApplication.run(DecisionApplication.class, args);
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw compile -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ye/decision/feign/ src/main/java/com/ye/decision/DecisionApplication.java
git commit -m "feat: add DownstreamClient interface, Feign clients, @EnableFeignClients"
```

---

## Task 6: Tool 逻辑层（Plain classes，无 @Component）

**Files:**
- Create: `src/main/java/com/ye/decision/tool/QueryRedisTool.java`
- Create: `src/main/java/com/ye/decision/tool/QueryMysqlTool.java`
- Create: `src/main/java/com/ye/decision/tool/CallExternalApiTool.java`
- Create: `src/test/java/com/ye/decision/tool/QueryRedisToolTest.java`
- Create: `src/test/java/com/ye/decision/tool/QueryMysqlToolTest.java`
- Create: `src/test/java/com/ye/decision/tool/CallExternalApiToolTest.java`

> **架构说明：** 这三个类是 Plain Java（无 @Component），但均实现 `Function<Req, String>`。`@Description` 和 `@Bean` 注册放在 Task 7 的 `ToolConfig.java` 中。Spring AI 的 `SpringBeanToolCallbackResolver` 识别 `Function` 类型 Bean 并读取工厂方法上的 `@Description`，这是 Spring AI 1.0.0 官方推荐的注册方式。

### 6a: QueryRedisTool

- [ ] **Step 1: 写 QueryRedisToolTest**

```java
// src/test/java/com/ye/decision/tool/QueryRedisToolTest.java
package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.QueryRedisReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryRedisToolTest {

    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
    ObjectMapper objectMapper = new ObjectMapper();
    QueryRedisTool tool;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        tool = new QueryRedisTool(redisTemplate, objectMapper);
    }

    @Test
    void string_keyFound_returnsValueAndFoundTrue() throws Exception {
        when(valueOps.get("user:1:name")).thenReturn("Alice");
        String result = tool.apply(new QueryRedisReq("user:1:name", "string"));
        assertThat(result).contains("\"value\":\"Alice\"").contains("\"found\":true");
    }

    @Test
    void string_keyNotFound_returnsFoundFalse() throws Exception {
        when(valueOps.get("missing:key")).thenReturn(null);
        String result = tool.apply(new QueryRedisReq("missing:key", "string"));
        assertThat(result).contains("\"found\":false");
    }

    @Test
    void hash_keyFound_returnsEntries() throws Exception {
        when(hashOps.entries("user:1:profile")).thenReturn(Map.of("age", 30));
        String result = tool.apply(new QueryRedisReq("user:1:profile", "hash"));
        assertThat(result).contains("\"found\":true").contains("age");
    }

    @Test
    void unknownDataType_returnsErrorJson() {
        String result = tool.apply(new QueryRedisReq("k", "unknown"));
        assertThat(result).contains("\"error\"");
    }
}
```

- [ ] **Step 2: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=QueryRedisToolTest -q 2>&1 | tail -10
```

- [ ] **Step 3: 实现 QueryRedisTool.java**

```java
// src/main/java/com/ye/decision/tool/QueryRedisTool.java
package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.QueryRedisReq;
import org.springframework.data.redis.core.*;

import java.util.*;
import java.util.function.Function;

public class QueryRedisTool implements Function<QueryRedisReq, String> {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public QueryRedisTool(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(QueryRedisReq req) {
        try {
            return switch (req.dataType()) {
                case "string" -> queryString(req.keyPattern());
                case "hash" -> queryHash(req.keyPattern());
                case "zset" -> queryZset(req.keyPattern());
                case "list" -> queryList(req.keyPattern());
                default -> errorJson("unsupported_type", "不支持的 dataType: " + req.dataType());
            };
        } catch (Exception e) {
            return errorJson("redis_error", e.getMessage());
        }
    }

    private String queryString(String key) throws Exception {
        Object value = redisTemplate.opsForValue().get(key);
        return buildResponse(key, "string", value, value != null);
    }

    private String queryHash(String key) throws Exception {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        boolean found = !entries.isEmpty();
        return buildResponse(key, "hash", found ? entries : null, found);
    }

    private String queryZset(String key) throws Exception {
        Set<Object> members = redisTemplate.opsForZSet().range(key, 0, -1);
        boolean found = members != null && !members.isEmpty();
        return buildResponse(key, "zset", found ? new ArrayList<>(members) : null, found);
    }

    private String queryList(String key) throws Exception {
        List<Object> items = redisTemplate.opsForList().range(key, 0, -1);
        boolean found = items != null && !items.isEmpty();
        return buildResponse(key, "list", found ? items : null, found);
    }

    private String buildResponse(String key, String type, Object value, boolean found) throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("key", key);
        resp.put("type", type);
        resp.put("value", value);
        resp.put("found", found);
        return objectMapper.writeValueAsString(resp);
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"queryRedisTool\"}";
    }
}
```

- [ ] **Step 4: 运行测试确认 PASS**

```bash
./mvnw test -Dtest=QueryRedisToolTest -q 2>&1 | tail -5
```

### 6b: QueryMysqlTool

- [ ] **Step 5: 写 QueryMysqlToolTest**

```java
// src/test/java/com/ye/decision/tool/QueryMysqlToolTest.java
package com.ye.decision.tool;

import com.ye.decision.domain.dto.QueryMysqlReq;
import com.ye.decision.feign.DownstreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryMysqlToolTest {

    DownstreamClient orderClient = mock(DownstreamClient.class);
    DownstreamClient userClient = mock(DownstreamClient.class);
    QueryMysqlTool tool;

    @BeforeEach
    void setUp() {
        // Map key 必须与 QueryMysqlReq.target 的合法值一致
        tool = new QueryMysqlTool(Map.of(
                "order-service", orderClient,
                "user-service", userClient
        ));
    }

    @Test
    void knownTarget_delegatesToCorrectClient() {
        when(orderClient.query("userId=1")).thenReturn("[{\"id\":1}]");
        String result = tool.apply(new QueryMysqlReq("order-service", "userId=1"));
        assertThat(result).isEqualTo("[{\"id\":1}]");
        verify(orderClient).query("userId=1");
    }

    @Test
    void unknownTarget_returnsErrorJson() {
        String result = tool.apply(new QueryMysqlReq("unknown-service", "anything"));
        assertThat(result).contains("\"error\"").contains("unknown_target");
    }

    @Test
    void feignException_returnsErrorJson() {
        when(orderClient.query(any())).thenThrow(new RuntimeException("timeout"));
        String result = tool.apply(new QueryMysqlReq("order-service", "userId=1"));
        assertThat(result).contains("\"error\"");
    }
}
```

- [ ] **Step 6: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=QueryMysqlToolTest -q 2>&1 | tail -10
```

- [ ] **Step 7: 实现 QueryMysqlTool.java**

```java
// src/main/java/com/ye/decision/tool/QueryMysqlTool.java
package com.ye.decision.tool;

import com.ye.decision.domain.dto.QueryMysqlReq;
import com.ye.decision.feign.DownstreamClient;

import java.util.Map;
import java.util.function.Function;

public class QueryMysqlTool implements Function<QueryMysqlReq, String> {

    private final Map<String, DownstreamClient> clients;

    public QueryMysqlTool(Map<String, DownstreamClient> clients) {
        this.clients = clients;
    }

    @Override
    public String apply(QueryMysqlReq req) {
        DownstreamClient client = clients.get(req.target());
        if (client == null) {
            return errorJson("unknown_target", "不支持的下游服务: " + req.target());
        }
        try {
            return client.query(req.query());
        } catch (Exception e) {
            return errorJson("feign_error", e.getMessage());
        }
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"queryMysqlTool\"}";
    }
}
```

- [ ] **Step 8: 运行测试确认 PASS**

```bash
./mvnw test -Dtest=QueryMysqlToolTest -q 2>&1 | tail -5
```

### 6c: CallExternalApiTool

- [ ] **Step 9: 写 CallExternalApiToolTest**

```java
// src/test/java/com/ye/decision/tool/CallExternalApiToolTest.java
package com.ye.decision.tool;

import com.ye.decision.domain.dto.ApiCallReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CallExternalApiToolTest {

    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    CallExternalApiTool tool;

    @BeforeEach
    void setUp() {
        tool = new CallExternalApiTool(
                restTemplate,
                "http://weather.test/current",
                "http://logistics.test/track",
                "http://exchange.test/rate"
        );
    }

    @Test
    void weather_callsCorrectUrl() {
        server.expect(requestTo("http://weather.test/current?city=%E5%8C%97%E4%BA%AC"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"temp\":\"20°C\"}", MediaType.APPLICATION_JSON));

        String result = tool.apply(new ApiCallReq("weather", "{\"city\":\"北京\"}"));
        assertThat(result).contains("20°C");
        server.verify();
    }

    @Test
    void unknownService_returnsErrorJson() {
        String result = tool.apply(new ApiCallReq("unknown", "{}"));
        assertThat(result).contains("\"error\"").contains("unknown_service");
    }

    @Test
    void httpError_returnsErrorJson() {
        server.expect(requestTo("http://logistics.test/track?trackingNo=SF123"))
                .andRespond(withServerError());
        String result = tool.apply(new ApiCallReq("logistics", "{\"trackingNo\":\"SF123\"}"));
        assertThat(result).contains("\"error\"");
    }
}
```

- [ ] **Step 10: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=CallExternalApiToolTest -q 2>&1 | tail -10
```

- [ ] **Step 11: 实现 CallExternalApiTool.java**

```java
// src/main/java/com/ye/decision/tool/CallExternalApiTool.java
package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.ApiCallReq;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.function.Function;

public class CallExternalApiTool implements Function<ApiCallReq, String> {

    private final RestTemplate restTemplate;
    private final String weatherUrl;
    private final String logisticsUrl;
    private final String exchangeRateUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CallExternalApiTool(RestTemplate restTemplate,
                               String weatherUrl,
                               String logisticsUrl,
                               String exchangeRateUrl) {
        this.restTemplate = restTemplate;
        this.weatherUrl = weatherUrl;
        this.logisticsUrl = logisticsUrl;
        this.exchangeRateUrl = exchangeRateUrl;
    }

    @Override
    public String apply(ApiCallReq req) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(req.params(), Map.class);
            return switch (req.service()) {
                case "weather" -> get(weatherUrl, params);
                case "logistics" -> get(logisticsUrl, params);
                case "exchange-rate" -> get(exchangeRateUrl, params);
                default -> errorJson("unknown_service", "不支持的外部服务: " + req.service());
            };
        } catch (Exception e) {
            return errorJson("api_error", e.getMessage());
        }
    }

    private String get(String baseUrl, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        params.forEach(builder::queryParam);
        return restTemplate.getForObject(builder.toUriString(), String.class);
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"callExternalApiTool\"}";
    }
}
```

- [ ] **Step 12: 运行全部 Tool 测试确认 PASS**

```bash
./mvnw test -Dtest="QueryRedisToolTest,QueryMysqlToolTest,CallExternalApiToolTest" -q 2>&1 | tail -5
```

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/ye/decision/tool/ src/test/java/com/ye/decision/tool/
git commit -m "feat: add 3 Tool classes (plain Java) with unit tests"
```

---

## Task 7: Config 层（RedisConfig + WebConfig + ToolConfig + AiConfig）

**Files:**
- Create: `src/main/java/com/ye/decision/config/RedisConfig.java`
- Create: `src/main/java/com/ye/decision/config/WebConfig.java`
- Create: `src/main/java/com/ye/decision/config/ToolConfig.java`
- Create: `src/main/java/com/ye/decision/config/AiConfig.java`

> **核心设计原则：** `@Description` 必须放在 `@Bean` 工厂方法上，Spring AI 才能读取并发送给 LLM 作为 function description。`ToolConfig` 专门承担这个职责，同时在此显式构造 `Map<String,DownstreamClient>`（key 为 `"order-service"`/`"user-service"`），避免 Spring 自动注入时 key 为 Bean 名称（camelCase）的问题。

- [ ] **Step 1: 创建 RedisConfig.java**

```java
// src/main/java/com/ye/decision/config/RedisConfig.java
package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory,
                                                        ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        Jackson2JsonRedisSerializer<Object> valueSerializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }
}
```

- [ ] **Step 2: 创建 WebConfig.java（含 RestTemplate Bean）**

```java
// src/main/java/com/ye/decision/config/WebConfig.java
package com.ye.decision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 3: 创建 ToolConfig.java（@Description 在 @Bean 方法上）**

```java
// src/main/java/com/ye/decision/config/ToolConfig.java
package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.feign.DownstreamClient;
import com.ye.decision.feign.OrderServiceClient;
import com.ye.decision.feign.UserServiceClient;
import com.ye.decision.tool.CallExternalApiTool;
import com.ye.decision.tool.QueryMysqlTool;
import com.ye.decision.tool.QueryRedisTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Configuration
public class ToolConfig {

    /**
     * 显式构造 Map，key 与 QueryMysqlReq.target 合法值对齐。
     * 不能依赖 Spring 自动注入 Map<String,DownstreamClient>，
     * 因为 Feign Bean 名称为 camelCase（"orderServiceClient"），与 "order-service" 不匹配。
     */
    @Bean
    public Map<String, DownstreamClient> downstreamClients(OrderServiceClient orderServiceClient,
                                                            UserServiceClient userServiceClient) {
        return Map.of(
            "order-service", orderServiceClient,
            "user-service", userServiceClient
        );
    }

    @Bean
    @Description("查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。")
    public QueryMysqlTool queryMysqlTool(Map<String, DownstreamClient> downstreamClients) {
        return new QueryMysqlTool(downstreamClients);
    }

    @Bean
    @Description("查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。")
    public QueryRedisTool queryRedisTool(RedisTemplate<String, Object> redisTemplate,
                                          ObjectMapper objectMapper) {
        return new QueryRedisTool(redisTemplate, objectMapper);
    }

    @Bean
    @Description("调用外部第三方服务，包括天气查询（weather）、物流追踪（logistics）、汇率查询（exchange-rate）。")
    public CallExternalApiTool callExternalApiTool(
        RestTemplate restTemplate,
        @Value("${decision.external.weather-url}") String weatherUrl,
        @Value("${decision.external.logistics-url}") String logisticsUrl,
        @Value("${decision.external.exchange-rate-url}") String exchangeRateUrl
    ) {
        return new CallExternalApiTool(restTemplate, weatherUrl, logisticsUrl, exchangeRateUrl);
    }
}
```

- [ ] **Step 4: 创建 AiConfig.java**

```java
// src/main/java/com/ye/decision/config/AiConfig.java
package com.ye.decision.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AiConfig {

    @Value("${decision.agent.memory-window-size:10}")
    private int memoryWindowSize;

    /**
     * ChatMemory 使用 InMemoryChatMemoryRepository。
     * RedisChatMemoryRepository 在 spring-ai 1.0.0 中不存在（首次出现于 2.0.0-M1），
     * 升级到 Spring AI 2.0.0+ 后可替换为 RedisChatMemoryRepository 实现跨重启持久化。
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(memoryWindowSize)
            .build();
    }

    @Bean
    @RefreshScope
    public ChatClient chatClient(DashScopeChatModel chatModel, ChatMemory chatMemory, String systemPrompt) {
        return ChatClient.builder(chatModel)
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultFunctions("queryMysqlTool", "queryRedisTool", "callExternalApiTool")
            .build();
    }

    @Bean
    public String systemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.md");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}

- [ ] **Step 5: 编译验证**

```bash
./mvnw compile -q 2>&1 | tail -10
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ye/decision/config/
git commit -m "feat: add RedisConfig, WebConfig(+RestTemplate), ToolConfig(@Description on @Bean), AiConfig"
```

---

## Task 8: AgentService

**Files:**
- Create: `src/main/java/com/ye/decision/service/AgentService.java`
- Create: `src/test/java/com/ye/decision/service/AgentServiceTest.java`

- [ ] **Step 1: 写 AgentServiceTest**

```java
// src/test/java/com/ye/decision/service/AgentServiceTest.java
package com.ye.decision.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    // RETURNS_DEEP_STUBS 让 mock 支持链式调用
    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    AgentService service = new AgentService(chatClient);

    @Test
    void chat_returnsFluxOfTokens() {
        when(chatClient.prompt()
            .user(any(String.class))
            .advisors(any())
            .stream()
            .content())
            .thenReturn(Flux.just("Hello", " World"));

        Flux<String> result = service.chat("session-1", "hi");

        StepVerifier.create(result)
            .expectNext("Hello")
            .expectNext(" World")
            .verifyComplete();
    }
}
```

> `RETURNS_DEEP_STUBS` 是 `org.mockito.Answers` 枚举值，也可通过 `Mockito.RETURNS_DEEP_STUBS` 引用。在 `import static org.mockito.Mockito.*;` 的情况下直接写 `RETURNS_DEEP_STUBS` 即可。

- [ ] **Step 2: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=AgentServiceTest -q 2>&1 | tail -10
```

- [ ] **Step 3: 实现 AgentService.java**

```java
// src/main/java/com/ye/decision/service/AgentService.java
package com.ye.decision.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentService {

    private final ChatClient chatClient;

    public AgentService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 执行 ReAct 推理，返回流式 token。
     * ChatMemory.CONVERSATION_ID 是 Spring AI 1.0.0 中的常量（替代旧版 CHAT_MEMORY_CONVERSATION_ID_KEY）。
     */
    public Flux<String> chat(String sessionId, String message) {
        return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .content();
    }
}
```

- [ ] **Step 4: 运行测试确认 PASS**

```bash
./mvnw test -Dtest=AgentServiceTest -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ye/decision/service/ src/test/java/com/ye/decision/service/
git commit -m "feat: add AgentService with ChatMemory.CONVERSATION_ID session isolation"
```

---

## Task 9: ChatController（SSE 端点）

**Files:**
- Create: `src/main/java/com/ye/decision/controller/ChatController.java`
- Create: `src/test/java/com/ye/decision/controller/ChatControllerTest.java`

- [ ] **Step 1: 写 ChatControllerTest**

```java
// src/test/java/com/ye/decision/controller/ChatControllerTest.java
package com.ye.decision.controller;

import com.ye.decision.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AgentService agentService;

    @Test
    void stream_returnsSseContentType() throws Exception {
        when(agentService.chat(any(), any())).thenReturn(Flux.just("hello"));

        mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void stream_emitsTokensAndDoneSignal() throws Exception {
        when(agentService.chat("s1", "hi")).thenReturn(Flux.just("Hello", " World"));

        String body = mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assert body.contains("Hello") : "Response should contain token 'Hello'";
        assert body.contains("[DONE]") : "Response should contain [DONE] signal";
    }
}
```

- [ ] **Step 2: 运行测试确认 FAIL**

```bash
./mvnw test -Dtest=ChatControllerTest -q 2>&1 | tail -10
```

- [ ] **Step 3: 实现 ChatController.java**

> Java 21：使用 `Executors.newVirtualThreadPerTaskExecutor()` 提供高并发 SSE 处理。

```java
// src/main/java/com/ye/decision/controller/ChatController.java
package com.ye.decision.controller;

import com.ye.decision.domain.dto.ChatRequest;
import com.ye.decision.service.AgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final AgentService agentService;
    // Java 21 虚拟线程：高并发 SSE 场景下避免阻塞平台线程
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            try {
                agentService.chat(request.sessionId(), request.message())
                        .doOnNext(token -> {
                            try {
                                emitter.send(token);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                emitter.send("[DONE]");
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(e -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data("{\"code\":500,\"msg\":\"" + e.getMessage() + "\",\"data\":null}"));
                                emitter.complete();
                            } catch (Exception ex) {
                                emitter.completeWithError(ex);
                            }
                        })
                        .blockLast(); // 在虚拟线程中阻塞等待完成，不占用平台线程
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

- [ ] **Step 4: 运行测试确认 PASS**

```bash
./mvnw test -Dtest=ChatControllerTest -q 2>&1 | tail -5
```

- [ ] **Step 5: 运行全部测试**

```bash
./mvnw test -q 2>&1 | tail -10
```

期望：`BUILD SUCCESS`，所有测试通过。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ye/decision/controller/ src/test/java/com/ye/decision/controller/
git commit -m "feat: add ChatController SSE with virtual threads and [DONE]/error event handling"
```

---

## Task 10: 冒烟验证（本地启动）

> 需要本地 Nacos（8848）和 Redis（6379）。

- [ ] **Step 1: 在 Nacos 控制台创建远端配置**

登录 http://127.0.0.1:8848/nacos，在 `dev` 命名空间新建：
- Data ID: `decision.yaml`，Group: `DEFAULT_GROUP`

内容：

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-xxxxxxxxx   # 替换为真实 DashScope API Key
      chat:
        model: qwen-plus
  data:
    redis:
      host: 127.0.0.1
      port: 6379

decision:
  agent:
    memory-window-size: 10
    max-tool-iterations: 5
  sse:
    timeout-ms: 180000
  redis:
    memory-ttl-hours: 24
  external:
    weather-url: https://api.weather.example.com/current
    logistics-url: https://api.logistics.example.com/track
    exchange-rate-url: https://api.exchange.example.com/rate
```

- [ ] **Step 2: 启动应用**

```bash
./mvnw spring-boot:run 2>&1 | grep -E "Started|ERROR" | head -5
```

期望：`Started DecisionApplication in X seconds`，无 `ERROR`。

- [ ] **Step 3: 发送测试请求**

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-001","message":"你好，请介绍一下你自己"}' \
  --no-buffer
```

期望：流式 SSE 输出，最后一行为 `data: [DONE]`。

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "chore: smoke test passed — Spring AI Alibaba Agent initial implementation complete"
```
