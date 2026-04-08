# 智能客服工单系统设计

> 日期：2026-04-08
> 方案：方案 A — WorkOrderTool 集成到现有 Agent

## 概述

在 decision-app 中新增 `WorkOrderTool`，与现有工具平级，Agent 在 ReAct 循环中自主调度。实现"信息收集→问题诊断→SOP 检索→创建工单→执行处理→关闭工单"的完整客服闭环。

全部 6 类现有工具（MySQL、Redis、外部 API、知识库、MCP 查询、MCP 写入）在客服场景中协同工作，Agent 全自主执行，包括创建工单、更新订单状态等。

## 一、数据模型

### 1.1 work_order 工单表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| order_no | VARCHAR(32) | 工单编号，格式 `WO20260408001` |
| type | VARCHAR(32) | 工单类型：ORDER / LOGISTICS / ACCOUNT / TECH_FAULT / CONSULTATION / OTHER |
| priority | VARCHAR(16) | 优先级：LOW / MEDIUM / HIGH / URGENT |
| status | VARCHAR(16) | 状态：PENDING → PROCESSING → RESOLVED → CLOSED |
| title | VARCHAR(256) | 工单标题（Agent 自动生成） |
| description | TEXT | 问题描述 |
| customer_id | VARCHAR(64) | 关联客户标识（手机号/用户ID） |
| assignee | VARCHAR(64) | 处理人（按类型自动分配） |
| assignee_group | VARCHAR(64) | 处理组 |
| resolution | TEXT | 处理结果 |
| session_id | VARCHAR(128) | 关联的对话 session |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| resolved_at | DATETIME | 解决时间 |

### 1.2 work_order_log 工单操作日志表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| order_no | VARCHAR(32) | 关联工单编号 |
| action | VARCHAR(32) | 操作：CREATE / ASSIGN / UPDATE_STATUS / ADD_NOTE / CLOSE |
| operator | VARCHAR(64) | 操作人（agent / 人名） |
| content | TEXT | 操作内容/备注 |
| created_at | DATETIME | 操作时间 |

### 1.3 assignee_rule 分配规则表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| work_order_type | VARCHAR(32) | 工单类型 |
| assignee_group | VARCHAR(64) | 处理组名称 |
| assignee | VARCHAR(64) | 默认处理人 |
| status | TINYINT | 1=启用 0=禁用 |

## 二、WorkOrderTool 设计

### 2.1 请求 DTO

```java
public record WorkOrderReq(
    String action,       // create / query / update / close
    String orderNo,      // 工单编号（query/update/close 时必填）
    String type,         // 工单类型（create 时必填）
    String priority,     // 优先级（create 时可选，默认 MEDIUM）
    String title,        // 标题（create 时必填）
    String description,  // 描述（create 时必填）
    String customerId,   // 客户标识（create 时必填）
    String status,       // 新状态（update 时必填）
    String resolution,   // 处理结果（update/close 时可选）
    String note          // 备注（update 时可选）
) {}
```

### 2.2 操作分发逻辑

| action | 做什么 | 工具链联动 |
|--------|--------|-----------|
| **create** | 根据 type 查 assignee_rule 自动指派 → 插入 work_order → 记录 work_order_log → 发邮件通知处理人 | Agent 先用其他工具查完信息，再调 create |
| **query** | 按 orderNo 或 customerId 查工单及其操作日志 | Agent 回答"我的工单进度"类问题 |
| **update** | 更新状态/添加备注 → 记录 log → 通知相关人 | Agent 按 SOP 处理完后更新工单 |
| **close** | 设状态为 CLOSED + 记录 resolution → 记录 log → 通知客户 | Agent 确认问题解决后关闭 |

### 2.3 Agent 调用示例（完整链路）

用户说："客户 13800001111 投诉物流慢"

```
Step 1: queryRedisTool          → 查缓存中用户信息
Step 2: queryMysqlTool          → 查 order-service 获取最近订单
Step 3: callExternalApiTool     → 查物流实时状态
Step 4: knowledgeSearchTool     → 检索"物流延迟"SOP
Step 5: workOrderTool(create)   → 创建 LOGISTICS 类型工单，自动指派物流组
Step 6: mcpExecuteSql           → 按 SOP 更新订单备注
Step 7: Agent 综合输出最终回答
```

### 2.4 关键词映射

```java
"workOrderTool", List.of("工单", "投诉", "报修", "申请", "反馈", "fault", "ticket", "complaint")
```

## 三、通知服务设计

### 3.1 接口抽象

```java
public interface NotificationService {
    void send(NotificationMessage message);
}
```

```java
public record NotificationMessage(
    String channel,      // EMAIL（后续可扩展 DINGTALK / WECHAT）
    String recipient,    // 收件人（邮箱地址）
    String subject,      // 主题
    String content       // 内容（支持 HTML）
) {}
```

### 3.2 实现

- `EmailNotificationService` — 使用 Spring Boot `JavaMailSender`
- 后续扩展钉钉/企微只需新增实现类 + 配置路由策略

### 3.3 触发时机

| 事件 | 通知谁 | 内容 |
|------|--------|------|
| 工单创建 | 处理人 | "新工单 WO20260408001 已分配给您，类型：物流问题，优先级：HIGH" |
| 状态变更 | 处理人 + 客户 | "工单 WO20260408001 状态已更新为：处理中" |
| 工单关闭 | 客户 | "您的工单 WO20260408001 已解决，处理结果：..." |

### 3.4 配置

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 465
    username: noreply@example.com
    password: xxx
    properties:
      mail.smtp.ssl.enable: true

decision:
  notification:
    enabled: true
    default-channel: EMAIL
```

### 3.5 与 WorkOrderTool 的关系

通知是 `WorkOrderService` 内部的副作用，不是独立 Tool。Agent 调用 `workOrderTool(create)` 时，Service 层自动触发通知，Agent 不需要感知通知细节。

## 四、System Prompt 扩展

在现有 `system-prompt.md` 末尾追加：

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

## 五、文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `docs/sql/work_order_tables.sql` | 3 张表建表脚本 |
| `domain/dto/WorkOrderReq.java` | 工具请求 DTO |
| `domain/entity/WorkOrder.java` | 工单实体 |
| `domain/entity/WorkOrderLog.java` | 工单操作日志实体 |
| `domain/entity/AssigneeRule.java` | 分配规则实体 |
| `domain/enums/WorkOrderType.java` | 工单类型枚举 |
| `domain/enums/WorkOrderStatus.java` | 工单状态枚举 |
| `domain/enums/WorkOrderPriority.java` | 优先级枚举 |
| `domain/enums/WorkOrderAction.java` | 操作日志动作枚举 |
| `mapper/WorkOrderMapper.java` | 工单 Mapper |
| `mapper/WorkOrderLogMapper.java` | 操作日志 Mapper |
| `mapper/AssigneeRuleMapper.java` | 分配规则 Mapper |
| `service/WorkOrderService.java` | 工单业务逻辑（CRUD + 自动指派） |
| `service/NotificationService.java` | 通知接口 |
| `service/EmailNotificationService.java` | 邮件通知实现 |
| `domain/dto/NotificationMessage.java` | 通知消息 DTO |
| `tool/WorkOrderTool.java` | Agent 工具类 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `config/ToolConfig.java` | 新增 workOrderTool Bean |
| `config/AiConfig.java` | 注册 workOrderTool 到 toolCallbacks |
| `service/AgentService.java` | TOOL_KEYWORDS 新增 workOrderTool 关键词 |
| `resources/prompt/system-prompt.md` | 追加客服工单流程指令 |
| `resources/application.yaml` | 新增邮件和通知配置 |

## 六、未来扩展（本次不实现）

- **多级审批流** — 高风险操作（如大额退款）需多级审批，工单状态增加 PENDING_APPROVAL
- **SLA 超时自动升级** — 工单超过设定时间未处理，自动提升优先级并通知上级
- **钉钉/企微通知** — 新增 `DingTalkNotificationService` / `WeChatNotificationService` 实现类
