# MCP Server 独立模块提取设计

## 背景

当前 MCP 数据库工具以嵌入式方式运行在 decision 主应用中，工具通过 Spring Bean 直接注册到 ChatClient。
需要将其拆为独立 Maven 模块，通过 SSE 协议暴露标准 MCP 端点，使外部 AI 客户端也能接入。

## 模块结构

```
decision/                          (父 pom，管理公共版本)
├── decision-app/                  (主应用，业务 + MCP Client)
└── decision-mcp-server/           (独立 MCP Server，SSE 传输)
```

## decision-mcp-server

### 定位
独立 Spring Boot 应用，端口 8081，通过 `spring-ai-starter-mcp-server-webmvc` 暴露 SSE 端点。

### 迁移代码
从 `com.ye.decision.mcp` 包整体搬入，包名改为 `com.ye.decision.mcp`：

| 包 | 内容 |
|---|------|
| `tool/` | ListTablesTool、DescribeTableTool、QueryDataTool、ExecuteSqlTool，改用 `@Tool` 注解 |
| `service/` | McpSqlExecutorService、McpSqlSecurityService、McpWhitelistService、McpAuditService |
| `config/` | McpProperties（前缀改为 `mcp`） |
| `domain/` | DTO、Entity、Enum |
| `exception/` | McpException、McpErrorCode |
| `mapper/` | McpAuditLogMapper、McpWhitelistMapper |
| `controller/` | McpToolController、McpWhitelistController（REST 管理接口保留） |

### 工具注册方式
不再手动构建 `FunctionToolCallback`，改用 `@Tool` 注解 + `ToolCallbackProvider`，Spring AI MCP Server 自动发现并暴露。

### 依赖
- `spring-ai-starter-mcp-server-webmvc`（SSE 传输）
- `spring-boot-starter-web`
- `mybatis-plus-spring-boot3-starter`（审计日志、白名单持久化）
- `mybatis-plus-jsqlparser`（SQL 解析校验）
- `mysql-connector-j`

### 配置（application.yaml）
```yaml
server:
  port: 8081

spring:
  ai:
    mcp:
      server:
        enabled: true
        name: decision-mcp-server
        version: 1.0.0

  datasource:
    url: jdbc:mysql://192.168.83.128:3306/decision
    username: root
    password: "1234"

mcp:
  enabled: true
  write-enabled: false
  query-timeout-seconds: 30
  max-row-limit: 1000
  default-row-limit: 100
  max-sql-length: 4096
  table-whitelist: []
  table-blacklist:
    - mcp_audit_log
    - mcp_table_whitelist
  forbidden-keywords:
    - TRUNCATE
    - DROP
    - ALTER
    - CREATE
    - GRANT
    - REVOKE
    - INTO OUTFILE
    - LOAD_FILE
    - SLEEP
    - BENCHMARK
```

## decision-app 改造

### 删除
- `com.ye.decision.mcp` 包下所有代码
- `AiConfig` 中 MCP 工具相关的 `@Autowired` 和 `FunctionToolCallback` 注册
- `bootstrap.yaml` 中 `decision.mcp` 配置段

### 新增
- 依赖 `spring-ai-starter-mcp-client`
- 配置 MCP Server SSE 地址：
```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            decision-mcp:
              url: http://localhost:8081
```
- `AiConfig` 中通过 MCP Client 自动发现的 `ToolCallback` 注册到 ChatClient

## 父 pom

将现有 `pom.xml` 改为父 pom（`<packaging>pom</packaging>`），管理公共版本号，`<modules>` 包含两个子模块。原有代码和资源整体移入 `decision-app/`。

## 数据流

```
用户 → decision-app (ChatClient) --SSE--> decision-mcp-server (工具执行) → MySQL
外部 AI 客户端 (Claude Desktop 等) --SSE--> decision-mcp-server
```

## 外部客户端接入示例

Claude Desktop `claude_desktop_config.json`：
```json
{
  "mcpServers": {
    "decision-db": {
      "url": "http://localhost:8081/sse"
    }
  }
}
```
