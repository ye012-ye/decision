package com.ye.decision.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.decision.mcp.domain.dto.McpAuditLogVO;
import com.ye.decision.mcp.domain.entity.McpAuditLogEntity;
import com.ye.decision.mcp.domain.enums.AuditStatus;
import com.ye.decision.mcp.domain.enums.SqlOperationType;
import com.ye.decision.mcp.mapper.McpAuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * MCP 审计日志服务。
 *
 * @author ye
 */
@Service
public class McpAuditService {

    private static final Logger log = LoggerFactory.getLogger(McpAuditService.class);
    private static final int MAX_SQL_LOG_LENGTH = 4096;

    private final McpAuditLogMapper auditLogMapper;

    public McpAuditService(McpAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 记录审计日志。审计失败不影响主流程。
     */
    public void log(String toolName, String sql, SqlOperationType opType,
                    AuditStatus status, String errorMsg, int rowsAffected, long executionMs) {
        try {
            McpAuditLogEntity entity = new McpAuditLogEntity();
            entity.setToolName(toolName);
            entity.setSqlText(sql != null && sql.length() > MAX_SQL_LOG_LENGTH
                ? sql.substring(0, MAX_SQL_LOG_LENGTH) : sql);
            entity.setOperationType(opType != null ? opType.getLabel() : "UNKNOWN");
            entity.setStatus(status.getLabel());
            entity.setErrorMessage(errorMsg);
            entity.setRowsAffected(rowsAffected);
            entity.setExecutionMs(executionMs);
            entity.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("Failed to write MCP audit log, tool={}, sql={}", toolName, sql, e);
        }
    }

    /**
     * 分页查询审计日志。
     */
    public IPage<McpAuditLogVO> queryLogs(int page, int size, String toolName, String status) {
        LambdaQueryWrapper<McpAuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (toolName != null && !toolName.isBlank()) {
            wrapper.eq(McpAuditLogEntity::getToolName, toolName);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(McpAuditLogEntity::getStatus, status);
        }
        wrapper.orderByDesc(McpAuditLogEntity::getCreatedAt);

        IPage<McpAuditLogEntity> entityPage = auditLogMapper.selectPage(
            new Page<>(page, size), wrapper
        );
        return entityPage.convert(McpAuditLogVO::from);
    }
}
