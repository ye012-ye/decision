package com.ye.decision.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.mcp.config.McpProperties;
import com.ye.decision.mcp.domain.dto.QueryDataReq;
import com.ye.decision.mcp.domain.enums.AuditStatus;
import com.ye.decision.mcp.domain.enums.SqlOperationType;
import com.ye.decision.mcp.service.McpAuditService;
import com.ye.decision.mcp.service.McpSqlExecutorService;
import com.ye.decision.mcp.service.McpSqlSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具：执行只读 SELECT 查询。
 * <p>
 * 先通过安全校验，再执行查询，最后记录审计日志。
 *
 * @author ye
 */
public class QueryDataTool implements Function<QueryDataReq, String> {

    private static final Logger log = LoggerFactory.getLogger(QueryDataTool.class);

    private final McpSqlSecurityService securityService;
    private final McpSqlExecutorService executorService;
    private final McpAuditService auditService;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public QueryDataTool(McpSqlSecurityService securityService,
                         McpSqlExecutorService executorService,
                         McpAuditService auditService,
                         McpProperties mcpProperties,
                         ObjectMapper objectMapper) {
        this.securityService = securityService;
        this.executorService = executorService;
        this.auditService = auditService;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(QueryDataReq req) {
        long start = System.currentTimeMillis();
        String sql = req.sql();

        try {
            // 1. 安全校验：readOnly=true 只允许 SELECT，拒绝写操作和危险 SQL
            securityService.validateSql(sql, true);

            // 2. 包装子查询强制 LIMIT，防止全表扫描返回过多数据
            String limitedSql = securityService.enforceRowLimit(sql, req.maxRows());

            // 3. 执行查询：JDBC 层面也设置了 maxRows 和 timeout 作为双重保护
            int effectiveLimit = req.maxRows() > 0
                ? Math.min(req.maxRows(), mcpProperties.getMaxRowLimit())
                : mcpProperties.getDefaultRowLimit();
            List<Map<String, Object>> rows = executorService.executeQuery(
                limitedSql, effectiveLimit, mcpProperties.getQueryTimeoutSeconds()
            );

            long elapsed = System.currentTimeMillis() - start;
            auditService.log("mcpQueryData", sql, SqlOperationType.SELECT,
                AuditStatus.SUCCESS, null, rows.size(), elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rowCount", rows.size());
            result.put("data", rows);
            result.put("executionMs", elapsed);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            // 无论是安全校验拒绝还是执行异常，都记录审计日志（状态=ERROR）
            // 返回 JSON 错误而非抛异常，因为 Agent 需要可读的错误信息来决定下一步
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("mcpQueryData", sql, SqlOperationType.SELECT,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("QueryDataTool failed: {}", sql, e);
            return "{\"error\":\"query_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
