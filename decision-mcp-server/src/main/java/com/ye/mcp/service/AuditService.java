package com.ye.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.mcp.domain.dto.AuditLogVO;
import com.ye.mcp.domain.entity.AuditLogEntity;
import com.ye.mcp.domain.enums.AuditStatus;
import com.ye.mcp.domain.enums.SqlOperationType;
import com.ye.mcp.mapper.AuditLogMapper;
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
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    /** 审计日志中 SQL 文本的最大存储长度，超出部分截断，防止超长 SQL 撑爆 DB 字段 */
    private static final int MAX_SQL_LOG_LENGTH = 4096;

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 记录审计日志。
     * 用 try-catch 吞掉所有异常——审计是旁路功能，不能因为审计表满/连接超时
     * 等原因导致正常的查询工具调用失败。
     */
    public void log(String toolName, String sql, SqlOperationType opType,
                    AuditStatus status, String errorMsg, int rowsAffected, long executionMs) {
        try {
            AuditLogEntity entity = new AuditLogEntity();
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
    public IPage<AuditLogVO> queryLogs(int page, int size, String toolName, String status) {
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (toolName != null && !toolName.isBlank()) {
            wrapper.eq(AuditLogEntity::getToolName, toolName);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AuditLogEntity::getStatus, status);
        }
        wrapper.orderByDesc(AuditLogEntity::getCreatedAt);

        IPage<AuditLogEntity> entityPage = auditLogMapper.selectPage(
            new Page<>(page, size), wrapper
        );
        // convert() 将 Entity 页转换为 VO 页，复用分页元信息（total、pages 等）
        return entityPage.convert(AuditLogVO::from);
    }
}
