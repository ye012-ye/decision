# MCP Server 独立模块提取 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将嵌入式 MCP 数据库工具拆为独立 Maven 模块 `decision-mcp-server`，通过 SSE 协议暴露标准 MCP 端点，主应用 `decision-app` 作为 MCP Client 连接。

**Architecture:** 父 pom 管理公共版本。`decision-mcp-server` 使用 `spring-ai-starter-mcp-server-webmvc` + `@McpTool` 注解暴露 4 个数据库工具。`decision-app` 使用 `spring-ai-starter-mcp-client` 通过 SSE 自动发现并注册 MCP 工具到 ChatClient。

**Tech Stack:** Spring Boot 3.3.5, Java 17, Spring AI 1.1.2, MyBatis-Plus 3.5.10.1, JSqlParser, Maven 多模块

---

## 文件清单

### 父 pom（改造）

| 文件路径 | 操作 | 职责 |
|---------|------|------|
| `pom.xml` | 修改 | 改为父 pom（packaging=pom），定义 modules 和公共版本管理 |

### decision-app（原项目瘦身）

| 文件路径 | 操作 | 职责 |
|---------|------|------|
| `decision-app/pom.xml` | 新建 | 子模块 pom，继承父 pom，增加 MCP Client 依赖 |
| `decision-app/src/main/java/com/ye/decision/config/AiConfig.java` | 修改 | 去掉 MCP 工具注册，注入 MCP Client 的 ToolCallbackProvider |
| `decision-app/src/main/java/com/ye/decision/common/GlobalExceptionHandler.java` | 修改 | 去掉 McpException 处理 |
| `decision-app/src/main/java/com/ye/decision/DecisionApplication.java` | 修改 | 去掉 mcp.mapper 的 MapperScan |
| `decision-app/src/main/resources/bootstrap.yaml` | 修改 | 去掉 decision.mcp 配置段，增加 MCP Client SSE 连接配置 |
| `decision-app/src/main/java/com/ye/decision/mcp/` | 删除 | 整个 mcp 包删除 |

### decision-mcp-server（新建模块）

| 文件路径 | 操作 | 职责 |
|---------|------|------|
| `decision-mcp-server/pom.xml` | 新建 | 子模块 pom，MCP Server WebMVC + MyBatis-Plus 依赖 |
| `decision-mcp-server/src/main/java/com/ye/mcp/McpServerApplication.java` | 新建 | Spring Boot 启动类 |
| `decision-mcp-server/src/main/java/com/ye/mcp/config/McpProperties.java` | 新建 | 配置属性，前缀 `mcp` |
| `decision-mcp-server/src/main/java/com/ye/mcp/tool/DatabaseTools.java` | 新建 | 4 个 @McpTool 方法合并到一个类 |
| `decision-mcp-server/src/main/java/com/ye/mcp/service/SqlExecutorService.java` | 新建 | SQL 执行服务（从原 McpSqlExecutorService 迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/service/SqlSecurityService.java` | 新建 | SQL 安全校验（从原 McpSqlSecurityService 迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/service/WhitelistService.java` | 新建 | 白名单管理（从原 McpWhitelistService 迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/service/AuditService.java` | 新建 | 审计日志（从原 McpAuditService 迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/domain/enums/SqlOperationType.java` | 新建 | 枚举（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/domain/enums/AuditStatus.java` | 新建 | 枚举（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/domain/entity/AuditLogEntity.java` | 新建 | Entity（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/domain/entity/WhitelistEntity.java` | 新建 | Entity（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/domain/dto/AuditLogVO.java` | 新建 | VO（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/domain/dto/TableWhitelistReq.java` | 新建 | DTO（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/exception/McpErrorCode.java` | 新建 | 错误码（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/exception/McpException.java` | 新建 | 异常类（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/exception/GlobalExceptionHandler.java` | 新建 | 独立的异常处理 |
| `decision-mcp-server/src/main/java/com/ye/mcp/common/Result.java` | 新建 | 统一响应（复制） |
| `decision-mcp-server/src/main/java/com/ye/mcp/mapper/AuditLogMapper.java` | 新建 | Mapper（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/mapper/WhitelistMapper.java` | 新建 | Mapper（迁移） |
| `decision-mcp-server/src/main/java/com/ye/mcp/controller/WhitelistController.java` | 新建 | 白名单管理 REST 接口 |
| `decision-mcp-server/src/main/java/com/ye/mcp/controller/AuditController.java` | 新建 | 审计日志查询 REST 接口 |
| `decision-mcp-server/src/main/resources/application.yaml` | 新建 | 数据源 + MCP Server 配置 |

---

## Task 1: 改造父 pom

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 将根 pom 改为父 pom**

把当前 `pom.xml` 改为聚合父 pom。保留 `<properties>` 和 `<dependencyManagement>`，把 `<dependencies>` 和 `<build>` 移除（子模块各自声明），添加 `<modules>` 和 `<packaging>pom</packaging>`。

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
    <packaging>pom</packaging>
    <name>decision</name>
    <description>Enterprise AI Agent Service</description>

    <modules>
        <module>decision-app</module>
        <module>decision-mcp-server</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-ai-alibaba.version>1.1.2.0</spring-ai-alibaba.version>
        <spring-cloud-alibaba.version>2023.0.3.2</spring-cloud-alibaba.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-ai.version>1.1.2</spring-ai.version>
        <redisson.version>3.35.0</redisson.version>
        <mybatis-plus.version>3.5.10.1</mybatis-plus.version>
        <milvus-sdk-version>2.6.13</milvus-sdk-version>
        <commons-lang3.version>3.17.0</commons-lang3.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type><scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-bom</artifactId>
                <version>${spring-ai-alibaba.version}</version>
                <type>pom</type><scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type><scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type><scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>spring-ai-alibaba</id>
            <name>Spring AI Alibaba</name>
            <url>https://maven.aliyun.com/repository/public</url>
            <releases><enabled>true</enabled></releases>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
        <repository>
            <id>spring-releases</id>
            <name>Spring Releases</name>
            <url>https://repo.spring.io/release</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>
</project>
```

- [ ] **Step 2: Commit**

```bash
git add pom.xml
git commit -m "refactor: convert root pom to parent aggregator for multi-module"
```

---

## Task 2: 创建 decision-app 子模块

**Files:**
- Create: `decision-app/pom.xml`
- Move: `src/` → `decision-app/src/`
- Move: `src/main/resources/` → `decision-app/src/main/resources/`

- [ ] **Step 1: 创建 decision-app 目录并移动源码**

```bash
mkdir decision-app
git mv src decision-app/
```

- [ ] **Step 2: 创建 decision-app/pom.xml**

原根 pom 的 `<dependencies>` 和 `<build>` 搬到这里，新增 `spring-ai-starter-mcp-client` 依赖。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ye</groupId>
        <artifactId>decision</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>decision-app</artifactId>
    <name>decision-app</name>
    <description>Enterprise AI Agent - Main Application</description>

    <dependencies>
        <!-- Web / SSE -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Jakarta Bean Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring AI Alibaba DashScope -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
            <version>${spring-ai-alibaba.version}</version>
        </dependency>

        <!-- MCP Client（SSE 连接 MCP Server） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-client</artifactId>
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

        <!-- Redisson -->
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>${redisson.version}</version>
        </dependency>

        <!-- Bootstrap Context -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>

        <!-- Load Balancer -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- RabbitMQ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- Milvus -->
        <dependency>
            <groupId>io.milvus</groupId>
            <artifactId>milvus-sdk-java</artifactId>
            <version>${milvus-sdk-version}</version>
        </dependency>

        <!-- Tika -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-tika-document-reader</artifactId>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
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

- [ ] **Step 3: 验证目录结构**

```bash
ls decision-app/src/main/java/com/ye/decision/
ls decision-app/src/main/resources/
```

预期：看到原有的 Java 源码和 resources 文件。

- [ ] **Step 4: Commit**

```bash
git add decision-app/
git commit -m "refactor: move source code into decision-app submodule"
```

---

## Task 3: 创建 decision-mcp-server 模块骨架

**Files:**
- Create: `decision-mcp-server/pom.xml`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/McpServerApplication.java`
- Create: `decision-mcp-server/src/main/resources/application.yaml`

- [ ] **Step 1: 创建目录结构**

```bash
mkdir -p decision-mcp-server/src/main/java/com/ye/mcp
mkdir -p decision-mcp-server/src/main/resources
mkdir -p decision-mcp-server/src/test/java/com/ye/mcp
```

- [ ] **Step 2: 创建 decision-mcp-server/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ye</groupId>
        <artifactId>decision</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>decision-mcp-server</artifactId>
    <name>decision-mcp-server</name>
    <description>MCP Database Tools Server (SSE)</description>

    <dependencies>
        <!-- MCP Server WebMVC（SSE 传输） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>

        <!-- Web（REST 管理接口） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- JSqlParser（SQL 安全校验） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
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

- [ ] **Step 3: 创建启动类 McpServerApplication.java**

```java
package com.ye.mcp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ye.mcp.mapper")
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建 application.yaml**

```yaml
server:
  port: 8081

spring:
  application:
    name: decision-mcp-server

  ai:
    mcp:
      server:
        name: decision-mcp-server
        version: 1.0.0

  datasource:
    url: jdbc:mysql://192.168.83.128:3306/decision?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: "1234"
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 5
      maximum-pool-size: 10
      connection-timeout: 30000

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto

# MCP 工具配置
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

logging:
  level:
    com.ye.mcp: DEBUG
    org.springframework.ai: INFO
```

- [ ] **Step 5: Commit**

```bash
git add decision-mcp-server/
git commit -m "feat: create decision-mcp-server module skeleton"
```

---

## Task 4: 迁移 domain、exception、mapper 层

**Files:**
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/domain/enums/SqlOperationType.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/domain/enums/AuditStatus.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/domain/entity/AuditLogEntity.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/domain/entity/WhitelistEntity.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/domain/dto/AuditLogVO.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/domain/dto/TableWhitelistReq.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/exception/McpErrorCode.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/exception/McpException.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/mapper/AuditLogMapper.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/mapper/WhitelistMapper.java`

- [ ] **Step 1: 创建目录结构**

```bash
mkdir -p decision-mcp-server/src/main/java/com/ye/mcp/{domain/{enums,entity,dto},exception,mapper}
```

- [ ] **Step 2: 迁移枚举类**

`decision-mcp-server/src/main/java/com/ye/mcp/domain/enums/SqlOperationType.java`：

```java
package com.ye.mcp.domain.enums;

public enum SqlOperationType {

    SELECT("SELECT"),
    INSERT("INSERT"),
    UPDATE("UPDATE"),
    DELETE("DELETE");

    private final String label;

    SqlOperationType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
```

`decision-mcp-server/src/main/java/com/ye/mcp/domain/enums/AuditStatus.java`：

```java
package com.ye.mcp.domain.enums;

public enum AuditStatus {

    SUCCESS("SUCCESS"),
    DENIED("DENIED"),
    ERROR("ERROR");

    private final String label;

    AuditStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
```

- [ ] **Step 3: 迁移 Entity**

`decision-mcp-server/src/main/java/com/ye/mcp/domain/entity/AuditLogEntity.java`：
从原 `McpAuditLogEntity` 复制，包名改为 `com.ye.mcp.domain.entity`，类名改为 `AuditLogEntity`。内容不变（@TableName("mcp_audit_log")，所有字段和 getter/setter 保持一致）。

`decision-mcp-server/src/main/java/com/ye/mcp/domain/entity/WhitelistEntity.java`：
从原 `McpWhitelistEntity` 复制，包名改为 `com.ye.mcp.domain.entity`，类名改为 `WhitelistEntity`。内容不变。

- [ ] **Step 4: 迁移 DTO**

`decision-mcp-server/src/main/java/com/ye/mcp/domain/dto/AuditLogVO.java`：

```java
package com.ye.mcp.domain.dto;

import com.ye.mcp.domain.entity.AuditLogEntity;

import java.time.LocalDateTime;

public record AuditLogVO(
    Long id,
    String toolName,
    String sqlText,
    String operationType,
    String status,
    String errorMessage,
    Integer rowsAffected,
    Long executionMs,
    LocalDateTime createdAt
) {
    public static AuditLogVO from(AuditLogEntity e) {
        return new AuditLogVO(
            e.getId(), e.getToolName(), e.getSqlText(), e.getOperationType(),
            e.getStatus(), e.getErrorMessage(), e.getRowsAffected(),
            e.getExecutionMs(), e.getCreatedAt()
        );
    }
}
```

`decision-mcp-server/src/main/java/com/ye/mcp/domain/dto/TableWhitelistReq.java`：

```java
package com.ye.mcp.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record TableWhitelistReq(
    @NotBlank(message = "表名不能为空") String tableName,
    String description
) {}
```

- [ ] **Step 5: 迁移 exception**

从原 `McpErrorCode` 和 `McpException` 复制，包名改为 `com.ye.mcp.exception`。内容不变。

- [ ] **Step 6: 迁移 Mapper**

`decision-mcp-server/src/main/java/com/ye/mcp/mapper/AuditLogMapper.java`：

```java
package com.ye.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.mcp.domain.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
```

`decision-mcp-server/src/main/java/com/ye/mcp/mapper/WhitelistMapper.java`：

```java
package com.ye.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.mcp.domain.entity.WhitelistEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WhitelistMapper extends BaseMapper<WhitelistEntity> {
}
```

- [ ] **Step 7: Commit**

```bash
git add decision-mcp-server/src/main/java/com/ye/mcp/domain/ decision-mcp-server/src/main/java/com/ye/mcp/exception/ decision-mcp-server/src/main/java/com/ye/mcp/mapper/
git commit -m "feat(mcp-server): migrate domain, exception, mapper layers"
```

---

## Task 5: 迁移 config 和 service 层

**Files:**
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/config/McpProperties.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/service/SqlExecutorService.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/service/SqlSecurityService.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/service/WhitelistService.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/service/AuditService.java`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p decision-mcp-server/src/main/java/com/ye/mcp/{config,service}
```

- [ ] **Step 2: 迁移 McpProperties**

`decision-mcp-server/src/main/java/com/ye/mcp/config/McpProperties.java`：
从原 `McpProperties` 复制，包名改为 `com.ye.mcp.config`，`@ConfigurationProperties` 前缀改为 `mcp`（去掉 `decision.` 前缀）。其他内容不变。

```java
package com.ye.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    private boolean enabled = true;
    private boolean writeEnabled = false;
    private int queryTimeoutSeconds = 30;
    private int maxRowLimit = 1000;
    private int defaultRowLimit = 100;
    private int maxSqlLength = 4096;
    private List<String> tableWhitelist = new ArrayList<>();
    private List<String> tableBlacklist = new ArrayList<>(List.of(
        "mcp_audit_log", "mcp_table_whitelist"
    ));
    private List<String> forbiddenKeywords = new ArrayList<>(List.of(
        "TRUNCATE", "DROP", "ALTER", "CREATE", "GRANT", "REVOKE",
        "INTO OUTFILE", "LOAD_FILE", "SLEEP", "BENCHMARK"
    ));

    // getter/setter 与原文件一致，此处省略（完整复制）
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isWriteEnabled() { return writeEnabled; }
    public void setWriteEnabled(boolean writeEnabled) { this.writeEnabled = writeEnabled; }
    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
    public int getMaxRowLimit() { return maxRowLimit; }
    public void setMaxRowLimit(int maxRowLimit) { this.maxRowLimit = maxRowLimit; }
    public int getDefaultRowLimit() { return defaultRowLimit; }
    public void setDefaultRowLimit(int defaultRowLimit) { this.defaultRowLimit = defaultRowLimit; }
    public int getMaxSqlLength() { return maxSqlLength; }
    public void setMaxSqlLength(int maxSqlLength) { this.maxSqlLength = maxSqlLength; }
    public List<String> getTableWhitelist() { return tableWhitelist; }
    public void setTableWhitelist(List<String> tableWhitelist) { this.tableWhitelist = tableWhitelist; }
    public List<String> getTableBlacklist() { return tableBlacklist; }
    public void setTableBlacklist(List<String> tableBlacklist) { this.tableBlacklist = tableBlacklist; }
    public List<String> getForbiddenKeywords() { return forbiddenKeywords; }
    public void setForbiddenKeywords(List<String> forbiddenKeywords) { this.forbiddenKeywords = forbiddenKeywords; }
}
```

- [ ] **Step 3: 迁移 4 个 Service**

逐个从原文件复制到新包路径，仅修改：
- 包名：`com.ye.decision.mcp.service` → `com.ye.mcp.service`
- import 路径：`com.ye.decision.mcp.*` → `com.ye.mcp.*`
- 类名前缀去掉 `Mcp`：`McpSqlExecutorService` → `SqlExecutorService`，以此类推

**SqlExecutorService.java**：从原 `McpSqlExecutorService` 复制，修改包名和 import。所有业务逻辑不变。

**SqlSecurityService.java**：从原 `McpSqlSecurityService` 复制，修改包名和 import。所有业务逻辑不变。

**WhitelistService.java**：从原 `McpWhitelistService` 复制，修改包名和 import。`McpWhitelistMapper` → `WhitelistMapper`，`McpWhitelistEntity` → `WhitelistEntity`。所有业务逻辑不变。

**AuditService.java**：从原 `McpAuditService` 复制，修改包名和 import。`McpAuditLogMapper` → `AuditLogMapper`，`McpAuditLogEntity` → `AuditLogEntity`，`McpAuditLogVO` → `AuditLogVO`。所有业务逻辑不变。

- [ ] **Step 4: Commit**

```bash
git add decision-mcp-server/src/main/java/com/ye/mcp/config/ decision-mcp-server/src/main/java/com/ye/mcp/service/
git commit -m "feat(mcp-server): migrate config and service layers"
```

---

## Task 6: 创建 @McpTool 工具类

**Files:**
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/tool/DatabaseTools.java`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p decision-mcp-server/src/main/java/com/ye/mcp/tool
```

- [ ] **Step 2: 创建 DatabaseTools.java**

将原来 4 个独立的 Tool 类合并为一个 `@Component`，每个方法用 `@McpTool` 注解。原来的 `Function<Req, String>` 模式改为直接方法调用。

```java
package com.ye.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.mcp.config.McpProperties;
import com.ye.mcp.domain.enums.AuditStatus;
import com.ye.mcp.domain.enums.SqlOperationType;
import com.ye.mcp.exception.McpErrorCode;
import com.ye.mcp.exception.McpException;
import com.ye.mcp.service.AuditService;
import com.ye.mcp.service.SqlExecutorService;
import com.ye.mcp.service.SqlSecurityService;
import com.ye.mcp.service.WhitelistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);

    private final SqlExecutorService executorService;
    private final SqlSecurityService securityService;
    private final WhitelistService whitelistService;
    private final AuditService auditService;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public DatabaseTools(SqlExecutorService executorService,
                         SqlSecurityService securityService,
                         WhitelistService whitelistService,
                         AuditService auditService,
                         McpProperties mcpProperties,
                         ObjectMapper objectMapper) {
        this.executorService = executorService;
        this.securityService = securityService;
        this.whitelistService = whitelistService;
        this.auditService = auditService;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "列出数据库中可查询的表。返回表名和注释。用于了解数据库结构。")
    public String listTables() {
        try {
            List<Map<String, String>> allTables = executorService.listTables();
            Set<String> blacklist = whitelistService.getBlacklist();
            Set<String> whitelist = whitelistService.getEffectiveWhitelist();

            List<Map<String, String>> filtered = allTables.stream()
                .filter(t -> {
                    String name = t.get("tableName").toLowerCase();
                    if (blacklist.contains(name)) return false;
                    return whitelist.isEmpty() || whitelist.contains(name);
                })
                .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", filtered.size());
            result.put("tables", filtered);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("listTables failed", e);
            return "{\"error\":\"list_tables_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "查看指定表的结构，包括列名、数据类型、索引信息。用于了解表结构后编写SQL。")
    public String describeTable(
            @ToolParam(description = "表名") String tableName) {
        try {
            if (tableName == null || tableName.isBlank()) {
                return "{\"error\":\"invalid_input\",\"message\":\"tableName 不能为空\"}";
            }
            if (!whitelistService.isAllowed(tableName)) {
                throw new McpException(McpErrorCode.TABLE_NOT_IN_WHITELIST, tableName);
            }
            Map<String, Object> description = executorService.describeTable(tableName);
            return objectMapper.writeValueAsString(description);
        } catch (McpException e) {
            return "{\"error\":\"" + e.getErrorCode().name() + "\",\"message\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("describeTable failed for: {}", tableName, e);
            return "{\"error\":\"describe_table_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "在数据库上执行只读SELECT查询。支持复杂查询、JOIN、聚合。返回查询结果JSON。需先用listTables和describeTable了解表结构。")
    public String queryData(
            @ToolParam(description = "SQL查询语句") String sql,
            @ToolParam(description = "最大返回行数，可选，默认100") int maxRows) {
        long start = System.currentTimeMillis();
        try {
            securityService.validateSql(sql, true);
            String limitedSql = securityService.enforceRowLimit(sql, maxRows);

            int effectiveLimit = maxRows > 0
                ? Math.min(maxRows, mcpProperties.getMaxRowLimit())
                : mcpProperties.getDefaultRowLimit();
            List<Map<String, Object>> rows = executorService.executeQuery(
                limitedSql, effectiveLimit, mcpProperties.getQueryTimeoutSeconds()
            );

            long elapsed = System.currentTimeMillis() - start;
            auditService.log("queryData", sql, SqlOperationType.SELECT,
                AuditStatus.SUCCESS, null, rows.size(), elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rowCount", rows.size());
            result.put("data", rows);
            result.put("executionMs", elapsed);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("queryData", sql, SqlOperationType.SELECT,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("queryData failed: {}", sql, e);
            return "{\"error\":\"query_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "在数据库执行INSERT/UPDATE/DELETE操作。仅在写操作启用时可用。需谨慎使用。")
    public String executeSql(
            @ToolParam(description = "SQL语句") String sql) {
        if (!mcpProperties.isWriteEnabled()) {
            return "{\"error\":\"write_not_enabled\",\"message\":\"写操作未启用\"}";
        }

        long start = System.currentTimeMillis();
        try {
            SqlOperationType opType = securityService.validateSql(sql, false);

            if (opType == SqlOperationType.SELECT) {
                return "{\"error\":\"use_query_tool\",\"message\":\"SELECT 请使用 queryData 工具\"}";
            }

            int affected = executorService.executeUpdate(sql, mcpProperties.getQueryTimeoutSeconds());

            long elapsed = System.currentTimeMillis() - start;
            auditService.log("executeSql", sql, opType,
                AuditStatus.SUCCESS, null, affected, elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("operationType", opType.getLabel());
            result.put("rowsAffected", affected);
            result.put("executionMs", elapsed);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("executeSql", sql, null,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("executeSql failed: {}", sql, e);
            return "{\"error\":\"execute_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add decision-mcp-server/src/main/java/com/ye/mcp/tool/
git commit -m "feat(mcp-server): create @McpTool database tools"
```

---

## Task 7: 迁移 controller 和 common 层

**Files:**
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/common/Result.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/exception/GlobalExceptionHandler.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/controller/WhitelistController.java`
- Create: `decision-mcp-server/src/main/java/com/ye/mcp/controller/AuditController.java`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p decision-mcp-server/src/main/java/com/ye/mcp/{common,controller}
```

- [ ] **Step 2: 创建 Result.java**

```java
package com.ye.mcp.common;

public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
```

- [ ] **Step 3: 创建 GlobalExceptionHandler.java**

```java
package com.ye.mcp.exception;

import com.ye.mcp.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(McpException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMcpException(McpException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.error(500, "服务内部错误");
    }
}
```

- [ ] **Step 4: 创建 WhitelistController.java**

```java
package com.ye.mcp.controller;

import com.ye.mcp.common.Result;
import com.ye.mcp.domain.dto.TableWhitelistReq;
import com.ye.mcp.domain.entity.WhitelistEntity;
import com.ye.mcp.service.WhitelistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/whitelist")
public class WhitelistController {

    private final WhitelistService whitelistService;

    public WhitelistController(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @GetMapping
    public Result<List<WhitelistEntity>> list() {
        return Result.ok(whitelistService.listAll());
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody TableWhitelistReq req) {
        whitelistService.addTable(req);
        return Result.ok(null);
    }

    @DeleteMapping("/{tableName}")
    public Result<Void> remove(@PathVariable String tableName) {
        whitelistService.removeTable(tableName);
        return Result.ok(null);
    }
}
```

- [ ] **Step 5: 创建 AuditController.java**

```java
package com.ye.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ye.mcp.common.Result;
import com.ye.mcp.domain.dto.AuditLogVO;
import com.ye.mcp.service.AuditService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mcp")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit-logs")
    public Result<IPage<AuditLogVO>> queryAuditLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String toolName,
        @RequestParam(required = false) String status
    ) {
        return Result.ok(auditService.queryLogs(page, size, toolName, status));
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add decision-mcp-server/src/main/java/com/ye/mcp/common/ decision-mcp-server/src/main/java/com/ye/mcp/exception/GlobalExceptionHandler.java decision-mcp-server/src/main/java/com/ye/mcp/controller/
git commit -m "feat(mcp-server): add controllers, Result, and exception handler"
```

---

## Task 8: 清理 decision-app 中的 MCP 代码

**Files:**
- Delete: `decision-app/src/main/java/com/ye/decision/mcp/` (整个目录)
- Modify: `decision-app/src/main/java/com/ye/decision/config/AiConfig.java`
- Modify: `decision-app/src/main/java/com/ye/decision/common/GlobalExceptionHandler.java`
- Modify: `decision-app/src/main/java/com/ye/decision/DecisionApplication.java`
- Modify: `decision-app/src/main/resources/bootstrap.yaml`

- [ ] **Step 1: 删除 mcp 包**

```bash
git rm -r decision-app/src/main/java/com/ye/decision/mcp/
```

- [ ] **Step 2: 修改 AiConfig.java**

去掉所有 MCP 工具相关的 import、`@Autowired` 字段、和 `FunctionToolCallback` 注册。注入 `ToolCallbackProvider` 来获取 MCP Client 自动发现的工具。

修改后的 `AiConfig.java`：

```java
package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.ApiCallReq;
import com.ye.decision.domain.dto.QueryMysqlReq;
import com.ye.decision.domain.dto.QueryRedisReq;
import com.ye.decision.mq.ChatMemoryPublisher;
import com.ye.decision.rag.domain.dto.KnowledgeSearchReq;
import com.ye.decision.tool.KnowledgeSearchTool;
import com.ye.decision.tool.CallExternalApiTool;
import com.ye.decision.tool.QueryMysqlTool;
import com.ye.decision.tool.QueryRedisTool;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class AiConfig {

    @Value("${decision.agent.memory-window-size:10}")
    private int memoryWindowSize;

    @Bean
    public ChatMemory chatMemory(RedissonClient redissonClient,
                                 ObjectMapper objectMapper,
                                 ChatMemoryPublisher publisher) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new RedissonChatMemoryRepository(redissonClient, objectMapper, publisher))
            .maxMessages(memoryWindowSize)
            .build();
    }

    // MCP Client 自动注入的 ToolCallbackProvider（来自 MCP Server 的工具）
    @Autowired(required = false)
    private ToolCallbackProvider mcpToolCallbackProvider;

    @Bean
    public List<ToolCallback> toolCallbacks(QueryMysqlTool queryMysqlTool,
                                            QueryRedisTool queryRedisTool,
                                            CallExternalApiTool callExternalApiTool,
                                            KnowledgeSearchTool knowledgeSearchTool) {
        List<ToolCallback> callbacks = new ArrayList<>(List.of(
            FunctionToolCallback.builder("queryMysqlTool", queryMysqlTool)
                .description("查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。")
                .inputType(QueryMysqlReq.class)
                .build(),
            FunctionToolCallback.builder("queryRedisTool", queryRedisTool)
                .description("查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。")
                .inputType(QueryRedisReq.class)
                .build(),
            FunctionToolCallback.builder("callExternalApiTool", callExternalApiTool)
                .description("调用外部第三方服务，包括天气查询（weather）、物流追踪（logistics）、汇率查询（exchange-rate）。")
                .inputType(ApiCallReq.class)
                .build(),
            FunctionToolCallback.builder("knowledgeSearchTool", knowledgeSearchTool)
                .description("在企业知识库中搜索相关文档。适用于查询产品文档、操作手册、FAQ、政策规范、技术文档等非结构化知识。需要指定知识库编码(kbCode)和查询内容(query)。")
                .inputType(KnowledgeSearchReq.class)
                .build()
        ));

        // 添加 MCP Client 自动发现的工具（来自 decision-mcp-server）
        if (mcpToolCallbackProvider != null) {
            callbacks.addAll(Arrays.asList(mcpToolCallbackProvider.getToolCallbacks()));
        }

        return callbacks;
    }

    @Bean
    public String systemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.md");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 3: 修改 GlobalExceptionHandler.java**

去掉 `McpException` 的 import 和 handler 方法：

删除以下 import：
```java
import com.ye.decision.mcp.exception.McpException;
```

删除以下方法：
```java
@ExceptionHandler(McpException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public Result<Void> handleMcpException(McpException e) {
    return Result.error(e.getCode(), e.getMessage());
}
```

- [ ] **Step 4: 修改 DecisionApplication.java**

`@MapperScan` 中去掉 `com.ye.decision.mcp.mapper`（当前只有 `com.ye.decision.mapper` 和 `com.ye.decision.rag.mapper`，没有 mcp.mapper，所以实际不需要改动。确认一下即可）。

- [ ] **Step 5: 修改 bootstrap.yaml**

删除 `decision.mcp` 配置段（从 `# MCP 数据库工具模块` 到 `- BENCHMARK`），替换为 MCP Client 连接配置。

在 `spring:` 下添加：

```yaml
  ai:
    # ... 已有的 dashscope 配置保持不变 ...
    mcp:
      client:
        sse:
          connections:
            decision-mcp:
              url: http://localhost:8081
```

删除 `decision:` 下的整个 `mcp:` 段。

- [ ] **Step 6: Commit**

```bash
git add -A decision-app/
git commit -m "refactor(decision-app): remove embedded MCP, add MCP Client connection"
```

---

## Task 9: 编译验证

**Files:** 无新文件

- [ ] **Step 1: 从根目录编译整个项目**

```bash
cd D:/java/code/decision
mvn clean compile -pl decision-mcp-server
```

预期：BUILD SUCCESS。如果有编译错误，根据错误信息修复 import 路径。

- [ ] **Step 2: 编译 decision-app**

```bash
mvn clean compile -pl decision-app
```

预期：BUILD SUCCESS。确认去掉 MCP 代码后没有残留引用。

- [ ] **Step 3: 运行 decision-app 测试**

```bash
mvn test -pl decision-app
```

预期：所有现有测试通过。MCP 相关的测试文件已随 mcp 包一起删除，不会影响。

- [ ] **Step 4: Commit（如有修复）**

```bash
git add -A
git commit -m "fix: resolve compilation issues after module extraction"
```

---

## Task 10: 集成测试

**Files:** 无新文件

- [ ] **Step 1: 启动 decision-mcp-server**

```bash
cd decision-mcp-server
mvn spring-boot:run
```

预期：应用启动在 8081 端口，日志中看到 MCP Server 注册了 4 个工具（listTables、describeTable、queryData、executeSql）。

- [ ] **Step 2: 验证 SSE 端点可访问**

```bash
curl http://localhost:8081/sse
```

预期：返回 SSE 流连接（text/event-stream）。

- [ ] **Step 3: 验证 REST 管理接口**

```bash
curl http://localhost:8081/api/mcp/whitelist
curl http://localhost:8081/api/mcp/audit-logs
```

预期：返回 JSON 响应。

- [ ] **Step 4: 启动 decision-app 并测试 MCP Client 连接**

在另一个终端：

```bash
cd decision-app
mvn spring-boot:run
```

预期：日志中看到 MCP Client 连接到 `http://localhost:8081`，发现 MCP 工具。

- [ ] **Step 5: 通过 decision-app 的 Chat 接口测试工具调用**

发送一个会触发数据库查询的聊天请求，确认 Agent 能正确调用 MCP Server 的工具。

- [ ] **Step 6: Final Commit**

```bash
git add -A
git commit -m "feat: complete MCP Server extraction to independent module"
```
