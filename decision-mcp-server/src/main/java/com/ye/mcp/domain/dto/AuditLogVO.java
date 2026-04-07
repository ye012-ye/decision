package com.ye.mcp.domain.dto;

import com.ye.mcp.domain.entity.AuditLogEntity;
import java.time.LocalDateTime;

public record AuditLogVO(
    Long id, String toolName, String sqlText, String operationType,
    String status, String errorMessage, Integer rowsAffected,
    Long executionMs, LocalDateTime createdAt
) {
    public static AuditLogVO from(AuditLogEntity e) {
        return new AuditLogVO(e.getId(), e.getToolName(), e.getSqlText(), e.getOperationType(),
            e.getStatus(), e.getErrorMessage(), e.getRowsAffected(), e.getExecutionMs(), e.getCreatedAt());
    }
}
