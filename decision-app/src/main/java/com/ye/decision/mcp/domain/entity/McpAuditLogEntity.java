package com.ye.decision.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * MCP SQL 执行审计日志。
 *
 * @author ye
 */
@TableName("mcp_audit_log")
public class McpAuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MCP 工具名称 */
    private String toolName;

    /** 执行的 SQL（截断至 4096 字符） */
    private String sqlText;

    /** 操作类型：SELECT/INSERT/UPDATE/DELETE */
    private String operationType;

    /** 执行状态：SUCCESS/DENIED/ERROR */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 影响行数 */
    private Integer rowsAffected;

    /** 执行耗时（毫秒） */
    private Long executionMs;

    /** Agent 会话 ID */
    private String sessionId;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getRowsAffected() { return rowsAffected; }
    public void setRowsAffected(Integer rowsAffected) { this.rowsAffected = rowsAffected; }
    public Long getExecutionMs() { return executionMs; }
    public void setExecutionMs(Long executionMs) { this.executionMs = executionMs; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
