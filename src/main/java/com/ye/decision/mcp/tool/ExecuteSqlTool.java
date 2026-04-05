package com.ye.decision.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.mcp.config.McpProperties;
import com.ye.decision.mcp.domain.dto.ExecuteSqlReq;
import com.ye.decision.mcp.domain.enums.AuditStatus;
import com.ye.decision.mcp.domain.enums.SqlOperationType;
import com.ye.decision.mcp.service.McpAuditService;
import com.ye.decision.mcp.service.McpSqlExecutorService;
import com.ye.decision.mcp.service.McpSqlSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具：执行 INSERT/UPDATE/DELETE 操作。
 * <p>
 * 仅在 {@code decision.mcp.write-enabled=true} 时可用（条件装配）。
 *
 * @author ye
 */
public class ExecuteSqlTool implements Function<ExecuteSqlReq, String> {

    private static final Logger log = LoggerFactory.getLogger(ExecuteSqlTool.class);

    private final McpSqlSecurityService securityService;
    private final McpSqlExecutorService executorService;
    private final McpAuditService auditService;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public ExecuteSqlTool(McpSqlSecurityService securityService,
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
    public String apply(ExecuteSqlReq req) {
        long start = System.currentTimeMillis();
        String sql = req.sql();

        try {
            // 安全校验（非只读模式）
            SqlOperationType opType = securityService.validateSql(sql, false);

            // 拒绝 SELECT（应使用 mcpQueryData）
            if (opType == SqlOperationType.SELECT) {
                return "{\"error\":\"use_query_tool\",\"message\":\"SELECT 请使用 mcpQueryData 工具\"}";
            }

            // 执行
            int affected = executorService.executeUpdate(sql, mcpProperties.getQueryTimeoutSeconds());

            long elapsed = System.currentTimeMillis() - start;
            auditService.log("mcpExecuteSql", sql, opType,
                AuditStatus.SUCCESS, null, affected, elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("operationType", opType.getLabel());
            result.put("rowsAffected", affected);
            result.put("executionMs", elapsed);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("mcpExecuteSql", sql, null,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("ExecuteSqlTool failed: {}", sql, e);
            return "{\"error\":\"execute_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
