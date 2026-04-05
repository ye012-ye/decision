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
            // 安全校验（只读模式）
            securityService.validateSql(sql, true);

            // 强制行数限制
            String limitedSql = securityService.enforceRowLimit(sql, req.maxRows());

            // 执行���询
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
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("mcpQueryData", sql, SqlOperationType.SELECT,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("QueryDataTool failed: {}", sql, e);
            return "{\"error\":\"query_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
