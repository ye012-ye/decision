# Smart Customer Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a WorkOrderTool to the existing decision-app Agent, enabling automated customer service with work order CRUD, auto-assignment, and email notifications.

**Architecture:** New `WorkOrderTool` (Function<WorkOrderReq, String>) sits alongside existing tools in the ReAct loop. It delegates to `WorkOrderService` for business logic (CRUD + auto-assign via `assignee_rule` table). `WorkOrderService` calls `NotificationService` (interface) with `EmailNotificationService` as the first implementation using Spring `JavaMailSender`.

**Tech Stack:** Java 17+, Spring Boot 3, MyBatis-Plus, Spring Mail (`JavaMailSender`), H2 (tests)

**Base path for all files:** `decision-app/src/main/java/com/ye/decision/`
**Test base path:** `decision-app/src/test/java/com/ye/decision/`

---

## File Structure

### New files (create)

| File | Responsibility |
|------|---------------|
| `docs/sql/work_order_tables.sql` | DDL for 3 tables |
| `domain/enums/WorkOrderType.java` | Enum: ORDER, LOGISTICS, ACCOUNT, TECH_FAULT, CONSULTATION, OTHER |
| `domain/enums/WorkOrderStatus.java` | Enum: PENDING, PROCESSING, RESOLVED, CLOSED |
| `domain/enums/WorkOrderPriority.java` | Enum: LOW, MEDIUM, HIGH, URGENT |
| `domain/enums/WorkOrderAction.java` | Enum: CREATE, ASSIGN, UPDATE_STATUS, ADD_NOTE, CLOSE |
| `domain/entity/WorkOrderEntity.java` | MyBatis-Plus entity for `work_order` |
| `domain/entity/WorkOrderLogEntity.java` | MyBatis-Plus entity for `work_order_log` |
| `domain/entity/AssigneeRuleEntity.java` | MyBatis-Plus entity for `assignee_rule` |
| `domain/dto/WorkOrderReq.java` | Tool input DTO (record) |
| `domain/dto/NotificationMessage.java` | Notification DTO (record) |
| `mapper/WorkOrderMapper.java` | BaseMapper<WorkOrderEntity> |
| `mapper/WorkOrderLogMapper.java` | BaseMapper<WorkOrderLogEntity> |
| `mapper/AssigneeRuleMapper.java` | BaseMapper<AssigneeRuleEntity> |
| `service/WorkOrderService.java` | Business logic: create/query/update/close + auto-assign |
| `service/NotificationService.java` | Interface with `void send(NotificationMessage)` |
| `service/EmailNotificationService.java` | Impl using JavaMailSender |
| `tool/WorkOrderTool.java` | Function<WorkOrderReq, String>, dispatches by action |

### New test files (create)

| File | Tests |
|------|-------|
| `tool/WorkOrderToolTest.java` | Unit tests for the tool dispatch logic |
| `service/WorkOrderServiceTest.java` | Unit tests for service business logic |

### Existing files (modify)

| File | Change |
|------|--------|
| `config/ToolConfig.java` | Add `workOrderTool` bean |
| `config/AiConfig.java` | Register workOrderTool in `toolCallbacks` list |
| `service/AgentService.java` | Add workOrderTool to `TOOL_KEYWORDS` |
| `src/main/resources/prompt/system-prompt.md` | Append customer service instructions |
| `src/main/resources/bootstrap.yaml` | Add `spring.mail.*` and `decision.notification.*` config |
| `src/test/resources/schema.sql` | Add 3 table DDLs for H2 test DB |

---

## Task 1: SQL Scripts

**Files:**
- Create: `docs/sql/work_order_tables.sql`
- Modify: `decision-app/src/test/resources/schema.sql`

- [ ] **Step 1: Create production DDL script**

Create `docs/sql/work_order_tables.sql`:

```sql
-- 工单表
CREATE TABLE IF NOT EXISTS work_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL UNIQUE COMMENT '工单编号 WOyyyyMMddNNN',
    type            VARCHAR(32)    NOT NULL COMMENT 'ORDER/LOGISTICS/ACCOUNT/TECH_FAULT/CONSULTATION/OTHER',
    priority        VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW/MEDIUM/HIGH/URGENT',
    status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
    title           VARCHAR(256)   NOT NULL,
    description     TEXT           NOT NULL,
    customer_id     VARCHAR(64)    NOT NULL COMMENT '客户标识（手机号/用户ID）',
    assignee        VARCHAR(64)    DEFAULT NULL COMMENT '处理人',
    assignee_group  VARCHAR(64)    DEFAULT NULL COMMENT '处理组',
    resolution      TEXT           DEFAULT NULL COMMENT '处理结果',
    session_id      VARCHAR(128)   DEFAULT NULL COMMENT '关联对话 session',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at     DATETIME       DEFAULT NULL,
    INDEX idx_order_no (order_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服工单';

-- 工单操作日志表
CREATE TABLE IF NOT EXISTS work_order_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL COMMENT '关联工单编号',
    action          VARCHAR(32)    NOT NULL COMMENT 'CREATE/ASSIGN/UPDATE_STATUS/ADD_NOTE/CLOSE',
    operator        VARCHAR(64)    NOT NULL COMMENT '操作人',
    content         TEXT           DEFAULT NULL COMMENT '操作内容/备注',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单操作日志';

-- 分配规则表
CREATE TABLE IF NOT EXISTS assignee_rule (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_type   VARCHAR(32)    NOT NULL COMMENT '工单类型',
    assignee_group    VARCHAR(64)    NOT NULL COMMENT '处理组名称',
    assignee          VARCHAR(64)    NOT NULL COMMENT '默认处理人',
    assignee_email    VARCHAR(128)   DEFAULT NULL COMMENT '处理人邮箱',
    status            TINYINT        NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    UNIQUE KEY uk_type (work_order_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单分配规则';

-- 初始分配规则
INSERT INTO assignee_rule (work_order_type, assignee_group, assignee, assignee_email) VALUES
('ORDER',        '订单组',   '订单专员',   'order@example.com'),
('LOGISTICS',    '物流组',   '物流专员',   'logistics@example.com'),
('ACCOUNT',      '用户组',   '用户专员',   'account@example.com'),
('TECH_FAULT',   '技术组',   '技术专员',   'tech@example.com'),
('CONSULTATION', '咨询组',   '咨询专员',   'consult@example.com'),
('OTHER',        '综合组',   '综合专员',   'general@example.com');
```

- [ ] **Step 2: Add H2-compatible DDL to test schema**

Append the following to `decision-app/src/test/resources/schema.sql` (after existing tables). H2 does not support `COMMENT` or `ENGINE` clauses, so use simplified DDL:

```sql
CREATE TABLE IF NOT EXISTS work_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL UNIQUE,
    type            VARCHAR(32)    NOT NULL,
    priority        VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    title           VARCHAR(256)   NOT NULL,
    description     TEXT           NOT NULL,
    customer_id     VARCHAR(64)    NOT NULL,
    assignee        VARCHAR(64)    DEFAULT NULL,
    assignee_group  VARCHAR(64)    DEFAULT NULL,
    resolution      TEXT           DEFAULT NULL,
    session_id      VARCHAR(128)   DEFAULT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP      DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS work_order_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL,
    action          VARCHAR(32)    NOT NULL,
    operator        VARCHAR(64)    NOT NULL,
    content         TEXT           DEFAULT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS assignee_rule (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_type   VARCHAR(32)    NOT NULL UNIQUE,
    assignee_group    VARCHAR(64)    NOT NULL,
    assignee          VARCHAR(64)    NOT NULL,
    assignee_email    VARCHAR(128)   DEFAULT NULL,
    status            TINYINT        NOT NULL DEFAULT 1
);
```

- [ ] **Step 3: Commit**

```bash
git add docs/sql/work_order_tables.sql decision-app/src/test/resources/schema.sql
git commit -m "feat(workorder): add DDL for work_order, work_order_log, assignee_rule tables"
```

---

## Task 2: Enums and DTOs

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/domain/enums/WorkOrderType.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/enums/WorkOrderStatus.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/enums/WorkOrderPriority.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/enums/WorkOrderAction.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderReq.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/NotificationMessage.java`

- [ ] **Step 1: Create WorkOrderType enum**

```java
package com.ye.decision.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderType {
    ORDER("ORDER", "订单问题"),
    LOGISTICS("LOGISTICS", "物流问题"),
    ACCOUNT("ACCOUNT", "账户问题"),
    TECH_FAULT("TECH_FAULT", "技术故障"),
    CONSULTATION("CONSULTATION", "咨询类"),
    OTHER("OTHER", "其他");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    WorkOrderType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
```

- [ ] **Step 2: Create WorkOrderStatus enum**

```java
package com.ye.decision.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderStatus {
    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    RESOLVED("RESOLVED", "已解决"),
    CLOSED("CLOSED", "已关闭");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    WorkOrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
```

- [ ] **Step 3: Create WorkOrderPriority enum**

```java
package com.ye.decision.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderPriority {
    LOW("LOW", "低"),
    MEDIUM("MEDIUM", "中"),
    HIGH("HIGH", "高"),
    URGENT("URGENT", "紧急");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    WorkOrderPriority(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
```

- [ ] **Step 4: Create WorkOrderAction enum**

```java
package com.ye.decision.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderAction {
    CREATE("CREATE", "创建"),
    ASSIGN("ASSIGN", "指派"),
    UPDATE_STATUS("UPDATE_STATUS", "更新状态"),
    ADD_NOTE("ADD_NOTE", "添加备注"),
    CLOSE("CLOSE", "关闭");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    WorkOrderAction(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
```

- [ ] **Step 5: Create WorkOrderReq DTO**

Follow the project pattern from `QueryRedisReq.java` — use record with `@JsonProperty`:

```java
package com.ye.decision.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkOrderReq(
    @JsonProperty(required = true) String action,
    String orderNo,
    String type,
    String priority,
    String title,
    String description,
    String customerId,
    String status,
    String resolution,
    String note
) {}
```

- [ ] **Step 6: Create NotificationMessage DTO**

```java
package com.ye.decision.domain.dto;

public record NotificationMessage(
    String channel,
    String recipient,
    String subject,
    String content
) {}
```

- [ ] **Step 7: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/domain/enums/WorkOrder*.java \
        decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderReq.java \
        decision-app/src/main/java/com/ye/decision/domain/dto/NotificationMessage.java
git commit -m "feat(workorder): add enums and DTOs for work order system"
```

---

## Task 3: Entities and Mappers

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/domain/entity/WorkOrderEntity.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/entity/WorkOrderLogEntity.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/entity/AssigneeRuleEntity.java`
- Create: `decision-app/src/main/java/com/ye/decision/mapper/WorkOrderMapper.java`
- Create: `decision-app/src/main/java/com/ye/decision/mapper/WorkOrderLogMapper.java`
- Create: `decision-app/src/main/java/com/ye/decision/mapper/AssigneeRuleMapper.java`

- [ ] **Step 1: Create WorkOrderEntity**

Follow existing pattern from `ChatMessageEntity.java` — manual getters/setters, `@TableName`, `@TableId(type = IdType.AUTO)`:

```java
package com.ye.decision.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;

import java.time.LocalDateTime;

@TableName("work_order")
public class WorkOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private WorkOrderType type;
    private WorkOrderPriority priority;
    private WorkOrderStatus status;
    private String title;
    private String description;
    private String customerId;
    private String assignee;
    private String assigneeGroup;
    private String resolution;
    private String sessionId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    public WorkOrderEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public WorkOrderType getType() { return type; }
    public void setType(WorkOrderType type) { this.type = type; }
    public WorkOrderPriority getPriority() { return priority; }
    public void setPriority(WorkOrderPriority priority) { this.priority = priority; }
    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public String getAssigneeGroup() { return assigneeGroup; }
    public void setAssigneeGroup(String assigneeGroup) { this.assigneeGroup = assigneeGroup; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
```

- [ ] **Step 2: Create WorkOrderLogEntity**

```java
package com.ye.decision.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ye.decision.domain.enums.WorkOrderAction;

import java.time.LocalDateTime;

@TableName("work_order_log")
public class WorkOrderLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private WorkOrderAction action;
    private String operator;
    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public WorkOrderLogEntity() {}

    public WorkOrderLogEntity(String orderNo, WorkOrderAction action, String operator, String content) {
        this.orderNo = orderNo;
        this.action = action;
        this.operator = operator;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public WorkOrderAction getAction() { return action; }
    public String getOperator() { return operator; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: Create AssigneeRuleEntity**

```java
package com.ye.decision.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ye.decision.domain.enums.WorkOrderType;

@TableName("assignee_rule")
public class AssigneeRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private WorkOrderType workOrderType;
    private String assigneeGroup;
    private String assignee;
    private String assigneeEmail;
    private Integer status;

    public AssigneeRuleEntity() {}

    public Long getId() { return id; }
    public WorkOrderType getWorkOrderType() { return workOrderType; }
    public String getAssigneeGroup() { return assigneeGroup; }
    public String getAssignee() { return assignee; }
    public String getAssigneeEmail() { return assigneeEmail; }
    public Integer getStatus() { return status; }
}
```

- [ ] **Step 4: Create WorkOrderMapper**

Follow project pattern from `ChatMessageMapper.java`:

```java
package com.ye.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.domain.entity.WorkOrderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrderEntity> {
}
```

- [ ] **Step 5: Create WorkOrderLogMapper**

```java
package com.ye.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkOrderLogMapper extends BaseMapper<WorkOrderLogEntity> {
}
```

- [ ] **Step 6: Create AssigneeRuleMapper**

```java
package com.ye.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.domain.entity.AssigneeRuleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssigneeRuleMapper extends BaseMapper<AssigneeRuleEntity> {
}
```

- [ ] **Step 7: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/domain/entity/WorkOrder*.java \
        decision-app/src/main/java/com/ye/decision/domain/entity/AssigneeRuleEntity.java \
        decision-app/src/main/java/com/ye/decision/mapper/WorkOrder*.java \
        decision-app/src/main/java/com/ye/decision/mapper/AssigneeRuleMapper.java
git commit -m "feat(workorder): add entities and mappers for work order tables"
```

---

## Task 4: NotificationService

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/service/NotificationService.java`
- Create: `decision-app/src/main/java/com/ye/decision/service/EmailNotificationService.java`
- Modify: `decision-app/src/main/resources/bootstrap.yaml`
- Modify: `decision-app/pom.xml`

- [ ] **Step 1: Add spring-boot-starter-mail dependency**

Add to `decision-app/pom.xml`, in the `<dependencies>` section after the RabbitMQ dependency:

```xml
        <!-- Mail -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>
```

- [ ] **Step 2: Create NotificationService interface**

```java
package com.ye.decision.service;

import com.ye.decision.domain.dto.NotificationMessage;

public interface NotificationService {
    void send(NotificationMessage message);
}
```

- [ ] **Step 3: Create EmailNotificationService**

```java
package com.ye.decision.service;

import com.ye.decision.domain.dto.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${decision.notification.enabled:true}")
    private boolean enabled;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromAddress;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(NotificationMessage message) {
        if (!enabled) {
            log.debug("Notification disabled, skipping: {}", message.subject());
            return;
        }
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.content(), true);
            mailSender.send(mimeMessage);
            log.info("Email sent to {} : {}", message.recipient(), message.subject());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", message.recipient(), e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Add mail and notification config to bootstrap.yaml**

Append inside the `spring:` block (after `servlet:` section, before the closing of `spring:`):

```yaml
  # Mail
  mail:
    host: ${MAIL_HOST:smtp.example.com}
    port: ${MAIL_PORT:465}
    username: ${MAIL_USERNAME:noreply@example.com}
    password: ${MAIL_PASSWORD:changeme}
    properties:
      mail.smtp.ssl.enable: true
```

Append inside the `decision:` block (after `external:` section):

```yaml
  notification:
    enabled: ${NOTIFICATION_ENABLED:true}
    default-channel: EMAIL
```

- [ ] **Step 5: Commit**

```bash
git add decision-app/pom.xml \
        decision-app/src/main/java/com/ye/decision/service/NotificationService.java \
        decision-app/src/main/java/com/ye/decision/service/EmailNotificationService.java \
        decision-app/src/main/resources/bootstrap.yaml
git commit -m "feat(workorder): add NotificationService interface and email implementation"
```

---

## Task 5: WorkOrderService

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/service/WorkOrderService.java`
- Create: `decision-app/src/test/java/com/ye/decision/service/WorkOrderServiceTest.java`

- [ ] **Step 1: Write the failing test for WorkOrderService**

```java
package com.ye.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ye.decision.domain.dto.NotificationMessage;
import com.ye.decision.domain.entity.AssigneeRuleEntity;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.mapper.AssigneeRuleMapper;
import com.ye.decision.mapper.WorkOrderLogMapper;
import com.ye.decision.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkOrderServiceTest {

    WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
    WorkOrderLogMapper logMapper = mock(WorkOrderLogMapper.class);
    AssigneeRuleMapper ruleMapper = mock(AssigneeRuleMapper.class);
    NotificationService notificationService = mock(NotificationService.class);
    WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(workOrderMapper, logMapper, ruleMapper, notificationService);
    }

    @Test
    void create_setsOrderNoAndAutoAssigns() {
        AssigneeRuleEntity rule = new AssigneeRuleEntity();
        setField(rule, "assignee", "物流专员");
        setField(rule, "assigneeGroup", "物流组");
        setField(rule, "assigneeEmail", "logistics@example.com");
        when(ruleMapper.selectOne(any(QueryWrapper.class))).thenReturn(rule);
        when(workOrderMapper.insert(any())).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);

        WorkOrderEntity result = service.create(
            WorkOrderType.LOGISTICS, WorkOrderPriority.HIGH,
            "物流延迟投诉", "客户反馈物流3天未更新", "13800001111", "session-1"
        );

        assertThat(result.getOrderNo()).startsWith("WO");
        assertThat(result.getAssignee()).isEqualTo("物流专员");
        assertThat(result.getAssigneeGroup()).isEqualTo("物流组");
        assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.PENDING);

        verify(logMapper).insert(any(WorkOrderLogEntity.class));
        verify(notificationService).send(any(NotificationMessage.class));
    }

    @Test
    void create_noRule_assigneeIsNull() {
        when(ruleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(workOrderMapper.insert(any())).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);

        WorkOrderEntity result = service.create(
            WorkOrderType.OTHER, WorkOrderPriority.MEDIUM,
            "其他问题", "描述", "user-1", "session-2"
        );

        assertThat(result.getAssignee()).isNull();
        verify(notificationService, never()).send(any());
    }

    @Test
    void queryByOrderNo_returnsEntity() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        when(workOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

        WorkOrderEntity result = service.queryByOrderNo("WO20260408001");

        assertThat(result).isNotNull();
        assertThat(result.getOrderNo()).isEqualTo("WO20260408001");
    }

    @Test
    void queryByCustomerId_returnsList() {
        when(workOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new WorkOrderEntity()));

        List<WorkOrderEntity> result = service.queryByCustomerId("13800001111");

        assertThat(result).hasSize(1);
    }

    @Test
    void updateStatus_changesStatusAndLogsAction() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "status", WorkOrderStatus.PENDING);
        when(workOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);
        when(workOrderMapper.updateById(any())).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);

        service.updateStatus("WO20260408001", WorkOrderStatus.PROCESSING, "开始处理", "agent");

        verify(workOrderMapper).updateById(any());
        verify(logMapper).insert(any(WorkOrderLogEntity.class));
    }

    @Test
    void close_setsResolvedStatusAndResolution() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "status", WorkOrderStatus.RESOLVED);
        when(workOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);
        when(workOrderMapper.updateById(any())).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);

        service.close("WO20260408001", "已补发快递", "agent");

        ArgumentCaptor<WorkOrderEntity> captor = ArgumentCaptor.forClass(WorkOrderEntity.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(captor.getValue().getResolution()).isEqualTo("已补发快递");
    }

    @Test
    void getLogsByOrderNo_returnsList() {
        when(logMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new WorkOrderLogEntity()));

        List<WorkOrderLogEntity> result = service.getLogsByOrderNo("WO20260408001");

        assertThat(result).hasSize(1);
    }

    /** Helper to set private fields on entities for test setup */
    @SuppressWarnings("unchecked")
    private static <T> void setField(Object target, String fieldName, T value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd decision-app && mvn test -pl . -Dtest=WorkOrderServiceTest -Dmaven.main.skip=true --no-transfer-progress`
Expected: Compilation failure — `WorkOrderService` class does not exist yet.

- [ ] **Step 3: Create WorkOrderService**

```java
package com.ye.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ye.decision.domain.dto.NotificationMessage;
import com.ye.decision.domain.entity.AssigneeRuleEntity;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.mapper.AssigneeRuleMapper;
import com.ye.decision.mapper.WorkOrderLogMapper;
import com.ye.decision.mapper.WorkOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper logMapper;
    private final AssigneeRuleMapper ruleMapper;
    private final NotificationService notificationService;

    private final AtomicInteger dailySeq = new AtomicInteger(0);
    private volatile String lastDate = "";

    public WorkOrderService(WorkOrderMapper workOrderMapper,
                            WorkOrderLogMapper logMapper,
                            AssigneeRuleMapper ruleMapper,
                            NotificationService notificationService) {
        this.workOrderMapper = workOrderMapper;
        this.logMapper = logMapper;
        this.ruleMapper = ruleMapper;
        this.notificationService = notificationService;
    }

    public WorkOrderEntity create(WorkOrderType type, WorkOrderPriority priority,
                                   String title, String description,
                                   String customerId, String sessionId) {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setOrderNo(generateOrderNo());
        entity.setType(type);
        entity.setPriority(priority != null ? priority : WorkOrderPriority.MEDIUM);
        entity.setStatus(WorkOrderStatus.PENDING);
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setCustomerId(customerId);
        entity.setSessionId(sessionId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Auto-assign
        AssigneeRuleEntity rule = ruleMapper.selectOne(
            new QueryWrapper<AssigneeRuleEntity>()
                .eq("work_order_type", type.getCode())
                .eq("status", 1)
        );
        if (rule != null) {
            entity.setAssignee(rule.getAssignee());
            entity.setAssigneeGroup(rule.getAssigneeGroup());
        }

        workOrderMapper.insert(entity);

        // Log
        logMapper.insert(new WorkOrderLogEntity(
            entity.getOrderNo(), WorkOrderAction.CREATE, "agent",
            "创建工单: " + title
        ));

        // Notify assignee
        if (rule != null && rule.getAssigneeEmail() != null) {
            notificationService.send(new NotificationMessage(
                "EMAIL", rule.getAssigneeEmail(),
                "新工单 " + entity.getOrderNo() + " 已分配给您",
                "类型：" + type.getLabel() + "，优先级：" + entity.getPriority().getLabel()
                    + "，标题：" + title + "，客户：" + customerId
            ));
        }

        log.info("Work order created: {}", entity.getOrderNo());
        return entity;
    }

    public WorkOrderEntity queryByOrderNo(String orderNo) {
        return workOrderMapper.selectOne(
            new QueryWrapper<WorkOrderEntity>().eq("order_no", orderNo)
        );
    }

    public List<WorkOrderEntity> queryByCustomerId(String customerId) {
        return workOrderMapper.selectList(
            new QueryWrapper<WorkOrderEntity>().eq("customer_id", customerId).orderByDesc("created_at")
        );
    }

    public void updateStatus(String orderNo, WorkOrderStatus newStatus, String note, String operator) {
        WorkOrderEntity entity = queryByOrderNo(orderNo);
        if (entity == null) {
            throw new IllegalArgumentException("工单不存在: " + orderNo);
        }
        entity.setStatus(newStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        if (newStatus == WorkOrderStatus.RESOLVED) {
            entity.setResolvedAt(LocalDateTime.now());
        }
        workOrderMapper.updateById(entity);

        logMapper.insert(new WorkOrderLogEntity(
            orderNo, WorkOrderAction.UPDATE_STATUS, operator,
            "状态变更为 " + newStatus.getLabel() + (note != null ? "，备注：" + note : "")
        ));
    }

    public void close(String orderNo, String resolution, String operator) {
        WorkOrderEntity entity = queryByOrderNo(orderNo);
        if (entity == null) {
            throw new IllegalArgumentException("工单不存在: " + orderNo);
        }
        entity.setStatus(WorkOrderStatus.CLOSED);
        entity.setResolution(resolution);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(entity);

        logMapper.insert(new WorkOrderLogEntity(
            orderNo, WorkOrderAction.CLOSE, operator,
            "关闭工单，处理结果：" + resolution
        ));
    }

    public List<WorkOrderLogEntity> getLogsByOrderNo(String orderNo) {
        return logMapper.selectList(
            new QueryWrapper<WorkOrderLogEntity>().eq("order_no", orderNo).orderByAsc("created_at")
        );
    }

    private String generateOrderNo() {
        String today = LocalDate.now().format(DATE_FMT);
        synchronized (this) {
            if (!today.equals(lastDate)) {
                lastDate = today;
                dailySeq.set(0);
            }
        }
        int seq = dailySeq.incrementAndGet();
        return "WO" + today + String.format("%03d", seq);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd decision-app && mvn test -pl . -Dtest=WorkOrderServiceTest --no-transfer-progress`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/service/WorkOrderService.java \
        decision-app/src/test/java/com/ye/decision/service/WorkOrderServiceTest.java
git commit -m "feat(workorder): add WorkOrderService with CRUD, auto-assign, and notification"
```

---

## Task 6: WorkOrderTool

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/tool/WorkOrderTool.java`
- Create: `decision-app/src/test/java/com/ye/decision/tool/WorkOrderToolTest.java`

- [ ] **Step 1: Write the failing test for WorkOrderTool**

```java
package com.ye.decision.tool;

import com.ye.decision.domain.dto.WorkOrderReq;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkOrderToolTest {

    WorkOrderService workOrderService = mock(WorkOrderService.class);
    WorkOrderTool tool;

    @BeforeEach
    void setUp() {
        tool = new WorkOrderTool(workOrderService);
    }

    @Test
    void create_returnsSuccessJson() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "type", WorkOrderType.LOGISTICS);
        setField(entity, "priority", WorkOrderPriority.HIGH);
        setField(entity, "status", WorkOrderStatus.PENDING);
        setField(entity, "assignee", "物流专员");
        setField(entity, "assigneeGroup", "物流组");
        when(workOrderService.create(any(), any(), any(), any(), any(), any())).thenReturn(entity);

        String result = tool.apply(new WorkOrderReq(
            "create", null, "LOGISTICS", "HIGH",
            "物流延迟", "快递3天没动", "13800001111",
            null, null, null
        ));

        assertThat(result).contains("WO20260408001").contains("success");
    }

    @Test
    void query_byOrderNo_returnsWorkOrder() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "status", WorkOrderStatus.PENDING);
        when(workOrderService.queryByOrderNo("WO20260408001")).thenReturn(entity);
        when(workOrderService.getLogsByOrderNo("WO20260408001")).thenReturn(List.of());

        String result = tool.apply(new WorkOrderReq(
            "query", "WO20260408001", null, null,
            null, null, null, null, null, null
        ));

        assertThat(result).contains("WO20260408001");
    }

    @Test
    void query_byCustomerId_returnsList() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        when(workOrderService.queryByCustomerId("13800001111")).thenReturn(List.of(entity));

        String result = tool.apply(new WorkOrderReq(
            "query", null, null, null,
            null, null, "13800001111", null, null, null
        ));

        assertThat(result).contains("WO20260408001");
    }

    @Test
    void update_callsUpdateStatus() {
        String result = tool.apply(new WorkOrderReq(
            "update", "WO20260408001", null, null,
            null, null, null, "PROCESSING", null, "开始处理"
        ));

        verify(workOrderService).updateStatus("WO20260408001", WorkOrderStatus.PROCESSING, "开始处理", "agent");
        assertThat(result).contains("success");
    }

    @Test
    void close_callsClose() {
        String result = tool.apply(new WorkOrderReq(
            "close", "WO20260408001", null, null,
            null, null, null, null, "已补发", null
        ));

        verify(workOrderService).close("WO20260408001", "已补发", "agent");
        assertThat(result).contains("success");
    }

    @Test
    void unknownAction_returnsError() {
        String result = tool.apply(new WorkOrderReq(
            "unknown", null, null, null,
            null, null, null, null, null, null
        ));

        assertThat(result).contains("error").contains("unknown_action");
    }

    @Test
    void create_missingRequiredFields_returnsError() {
        String result = tool.apply(new WorkOrderReq(
            "create", null, null, null,
            null, null, null, null, null, null
        ));

        assertThat(result).contains("error").contains("missing_field");
    }

    @SuppressWarnings("unchecked")
    private static <T> void setField(Object target, String fieldName, T value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd decision-app && mvn test -pl . -Dtest=WorkOrderToolTest -Dmaven.main.skip=true --no-transfer-progress`
Expected: Compilation failure — `WorkOrderTool` class does not exist yet.

- [ ] **Step 3: Create WorkOrderTool**

Follow the existing pattern from `QueryRedisTool.java` — implements `Function<WorkOrderReq, String>`, dispatches by action field:

```java
package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.WorkOrderReq;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.service.WorkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WorkOrderTool implements Function<WorkOrderReq, String> {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderTool.class);
    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkOrderTool(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @Override
    public String apply(WorkOrderReq req) {
        try {
            return switch (req.action()) {
                case "create" -> doCreate(req);
                case "query"  -> doQuery(req);
                case "update" -> doUpdate(req);
                case "close"  -> doClose(req);
                default       -> errorJson("unknown_action", "不支持的操作: " + req.action());
            };
        } catch (Exception e) {
            log.error("WorkOrderTool error: action={}", req.action(), e);
            return errorJson("tool_error", e.getMessage());
        }
    }

    private String doCreate(WorkOrderReq req) throws Exception {
        if (req.type() == null || req.title() == null || req.description() == null || req.customerId() == null) {
            return errorJson("missing_field", "create 操作必须提供 type, title, description, customerId");
        }
        WorkOrderType type = WorkOrderType.valueOf(req.type());
        WorkOrderPriority priority = req.priority() != null
            ? WorkOrderPriority.valueOf(req.priority()) : WorkOrderPriority.MEDIUM;

        WorkOrderEntity entity = workOrderService.create(type, priority, req.title(), req.description(), req.customerId(), null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", entity.getOrderNo());
        result.put("type", entity.getType());
        result.put("priority", entity.getPriority());
        result.put("status", entity.getStatus());
        result.put("assignee", entity.getAssignee());
        result.put("assigneeGroup", entity.getAssigneeGroup());
        return objectMapper.writeValueAsString(result);
    }

    private String doQuery(WorkOrderReq req) throws Exception {
        if (req.orderNo() != null) {
            WorkOrderEntity entity = workOrderService.queryByOrderNo(req.orderNo());
            if (entity == null) {
                return errorJson("not_found", "工单不存在: " + req.orderNo());
            }
            List<WorkOrderLogEntity> logs = workOrderService.getLogsByOrderNo(req.orderNo());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workOrder", entityToMap(entity));
            result.put("logs", logs.stream().map(this::logToMap).toList());
            return objectMapper.writeValueAsString(result);
        } else if (req.customerId() != null) {
            List<WorkOrderEntity> list = workOrderService.queryByCustomerId(req.customerId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", list.size());
            result.put("workOrders", list.stream().map(this::entityToMap).toList());
            return objectMapper.writeValueAsString(result);
        } else {
            return errorJson("missing_field", "query 操作需要提供 orderNo 或 customerId");
        }
    }

    private String doUpdate(WorkOrderReq req) throws Exception {
        if (req.orderNo() == null || req.status() == null) {
            return errorJson("missing_field", "update 操作必须提供 orderNo 和 status");
        }
        WorkOrderStatus newStatus = WorkOrderStatus.valueOf(req.status());
        workOrderService.updateStatus(req.orderNo(), newStatus, req.note(), "agent");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", req.orderNo());
        result.put("newStatus", newStatus);
        return objectMapper.writeValueAsString(result);
    }

    private String doClose(WorkOrderReq req) throws Exception {
        if (req.orderNo() == null) {
            return errorJson("missing_field", "close 操作必须提供 orderNo");
        }
        workOrderService.close(req.orderNo(), req.resolution(), "agent");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", req.orderNo());
        result.put("status", "CLOSED");
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> entityToMap(WorkOrderEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderNo", e.getOrderNo());
        map.put("type", e.getType());
        map.put("priority", e.getPriority());
        map.put("status", e.getStatus());
        map.put("title", e.getTitle());
        map.put("description", e.getDescription());
        map.put("customerId", e.getCustomerId());
        map.put("assignee", e.getAssignee());
        map.put("assigneeGroup", e.getAssigneeGroup());
        map.put("resolution", e.getResolution());
        map.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        map.put("resolvedAt", e.getResolvedAt() != null ? e.getResolvedAt().toString() : null);
        return map;
    }

    private Map<String, Object> logToMap(WorkOrderLogEntity l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", l.getAction());
        map.put("operator", l.getOperator());
        map.put("content", l.getContent());
        map.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
        return map;
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"workOrderTool\"}";
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd decision-app && mvn test -pl . -Dtest=WorkOrderToolTest --no-transfer-progress`
Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/tool/WorkOrderTool.java \
        decision-app/src/test/java/com/ye/decision/tool/WorkOrderToolTest.java
git commit -m "feat(workorder): add WorkOrderTool with create/query/update/close actions"
```

---

## Task 7: Wire into Agent

**Files:**
- Modify: `decision-app/src/main/java/com/ye/decision/config/ToolConfig.java`
- Modify: `decision-app/src/main/java/com/ye/decision/config/AiConfig.java`
- Modify: `decision-app/src/main/java/com/ye/decision/service/AgentService.java`

- [ ] **Step 1: Add workOrderTool bean to ToolConfig.java**

Add this import at the top of `ToolConfig.java`:

```java
import com.ye.decision.tool.WorkOrderTool;
import com.ye.decision.service.WorkOrderService;
```

Add this bean method inside the `ToolConfig` class (after the `callExternalApiTool` bean):

```java
    @Bean
    @Description("管理客服工单：创建、查询、更新状态、关闭。支持 action: create/query/update/close。")
    public WorkOrderTool workOrderTool(WorkOrderService workOrderService) {
        return new WorkOrderTool(workOrderService);
    }
```

- [ ] **Step 2: Register workOrderTool in AiConfig.java toolCallbacks**

Add this import at the top of `AiConfig.java`:

```java
import com.ye.decision.domain.dto.WorkOrderReq;
import com.ye.decision.tool.WorkOrderTool;
```

In the `toolCallbacks` method, add `WorkOrderTool workOrderTool` as a parameter and add it to the callbacks list. Change the method signature to:

```java
    @Bean
    public List<ToolCallback> toolCallbacks(QueryMysqlTool queryMysqlTool,
                                            QueryRedisTool queryRedisTool,
                                            CallExternalApiTool callExternalApiTool,
                                            KnowledgeSearchTool knowledgeSearchTool,
                                            WorkOrderTool workOrderTool) {
```

Add this entry to the `List.of(...)` inside the method, after the `knowledgeSearchTool` entry:

```java
            ,FunctionToolCallback.builder("workOrderTool", workOrderTool)
                .description("管理客服工单：创建(create)、查询(query)、更新状态(update)、关闭(close)。创建时需提供 type/title/description/customerId，会自动指派处理人并发送通知。")
                .inputType(WorkOrderReq.class)
                .build()
```

- [ ] **Step 3: Add workOrderTool to TOOL_KEYWORDS in AgentService.java**

In `AgentService.java`, add this entry to the `TOOL_KEYWORDS` map. The existing map uses `Map.of()` which supports up to 10 entries. Currently there are 6 entries, so adding 1 more is fine:

```java
        "workOrderTool", List.of("工单", "投诉", "报修", "申请", "反馈", "fault", "ticket", "complaint")
```

The full `TOOL_KEYWORDS` map becomes:

```java
    private static final Map<String, List<String>> TOOL_KEYWORDS = Map.of(
        "callExternalApiTool", List.of("天气", "weather", "物流", "logistics", "快递", "汇率", "exchange"),
        "knowledgeSearchTool", List.of("知识库", "文档", "手册", "faq", "政策", "规范", "knowledge", "搜索知识"),
        "mcpListTables", List.of("数据库", "有哪些表", "列出表", "table", "schema", "所有表"),
        "mcpDescribeTable", List.of("表结构", "字段", "列", "column", "describe", "索引"),
        "mcpQueryData", List.of("查询", "sql", "select", "统计", "报表", "数据分析", "聚合"),
        "mcpExecuteSql", List.of("插入", "更新", "删除", "insert", "update", "delete", "修改数据"),
        "workOrderTool", List.of("工单", "投诉", "报修", "申请", "反馈", "fault", "ticket", "complaint")
    );
```

- [ ] **Step 4: Verify compilation**

Run: `cd decision-app && mvn compile -pl . --no-transfer-progress`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add decision-app/src/main/java/com/ye/decision/config/ToolConfig.java \
        decision-app/src/main/java/com/ye/decision/config/AiConfig.java \
        decision-app/src/main/java/com/ye/decision/service/AgentService.java
git commit -m "feat(workorder): wire WorkOrderTool into Agent tool chain"
```

---

## Task 8: System Prompt Update

**Files:**
- Modify: `decision-app/src/main/resources/prompt/system-prompt.md`

- [ ] **Step 1: Append customer service instructions to system-prompt.md**

Add the following at the end of `system-prompt.md`:

```markdown

## 客服工单能力
- workOrderTool：创建、查询、更新、关闭工单。支持 action: create/query/update/close

## 客服处理流程（必须严格遵守）
当用户提出投诉、报修、申请等需要跟进的问题时，按以下流程执行：

1. **信息收集** — 先用 queryRedisTool / queryMysqlTool 查询客户信息和相关业务数据
2. **问题诊断** — 根据问题类型调用对应工具（如物流问题调 callExternalApiTool）
3. **SOP 检索** — 用 knowledgeSearchTool 检索对应类型的处理规范
4. **创建工单** — 调用 workOrderTool(create)，必须包含：type、title、description、customerId、priority
5. **执行处理** — 按 SOP 执行具体操作（如更新订单状态、记录备注等）
6. **更新工单** — 处理完成后调用 workOrderTool(update/close) 记录结果

## 工单分类规则
- 涉及订单查询、改单、取消、退款 → type=ORDER
- 涉及物流延迟、丢件、错发 → type=LOGISTICS
- 涉及用户信息修改、账号异常、权限 → type=ACCOUNT
- 涉及系统报错、功能异常 → type=TECH_FAULT
- 涉及产品咨询、政策咨询 → type=CONSULTATION
- 以上都不匹配 → type=OTHER

## 优先级判定规则
- URGENT：客户明确表示紧急/资金损失/大面积故障
- HIGH：影响正常使用/投诉类
- MEDIUM：一般咨询和请求（默认）
- LOW：建议类、非紧急反馈
```

- [ ] **Step 2: Commit**

```bash
git add decision-app/src/main/resources/prompt/system-prompt.md
git commit -m "feat(workorder): add customer service workflow to system prompt"
```

---

## Task 9: Run All Tests

- [ ] **Step 1: Run the full test suite**

Run: `cd decision-app && mvn test -pl . --no-transfer-progress`
Expected: All tests pass, including existing tests (`QueryRedisToolTest`, `CallExternalApiToolTest`, `QueryMysqlToolTest`, `AgentServiceTest`, `ChatControllerTest`) and new tests (`WorkOrderToolTest`, `WorkOrderServiceTest`).

- [ ] **Step 2: If any test fails, fix the issue and re-run**

- [ ] **Step 3: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix(workorder): fix test issues from integration"
```
