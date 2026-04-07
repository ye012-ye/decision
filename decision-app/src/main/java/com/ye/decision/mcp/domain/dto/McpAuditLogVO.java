package com.ye.decision.mcp.domain.dto;

import com.ye.decision.mcp.domain.entity.McpAuditLogEntity;

import java.time.LocalDateTime;

/**
 * 审计日志响应。
 *
 * @author ye
 */
public record McpAuditLogVO(
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
    public static McpAuditLogVO from(McpAuditLogEntity e) {
        return new McpAuditLogVO(
            e.getId(), e.getToolName(), e.getSqlText(), e.getOperationType(),
            e.getStatus(), e.getErrorMessage(), e.getRowsAffected(),
            e.getExecutionMs(), e.getCreatedAt()
        );
    }
}
