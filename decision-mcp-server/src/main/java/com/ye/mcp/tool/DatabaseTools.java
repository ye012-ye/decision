package com.ye.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.mcp.config.McpProperties;
import com.ye.mcp.domain.enums.AuditStatus;
import com.ye.mcp.domain.enums.SqlOperationType;
import com.ye.mcp.exception.McpErrorCode;
import com.ye.mcp.exception.McpException;
import com.ye.mcp.service.AuditService;
import com.ye.mcp.service.SqlExecutorService;
import com.ye.mcp.service.SqlSecurityService;
import com.ye.mcp.service.WhitelistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);

    private final SqlExecutorService executorService;
    private final SqlSecurityService securityService;
    private final WhitelistService whitelistService;
    private final AuditService auditService;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public DatabaseTools(SqlExecutorService executorService,
                         SqlSecurityService securityService,
                         WhitelistService whitelistService,
                         AuditService auditService,
                         McpProperties mcpProperties,
                         ObjectMapper objectMapper) {
        this.executorService = executorService;
        this.securityService = securityService;
        this.whitelistService = whitelistService;
        this.auditService = auditService;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "列出数据库中可查询的表。返回表名和注释。用于了解数据库结构。")
    public String listTables() {
        try {
            List<Map<String, String>> allTables = executorService.listTables();
            Set<String> blacklist = whitelistService.getBlacklist();
            Set<String> whitelist = whitelistService.getEffectiveWhitelist();

            List<Map<String, String>> filtered = allTables.stream()
                .filter(t -> {
                    String name = t.get("tableName").toLowerCase();
                    if (blacklist.contains(name)) {
                        return false;
                    }
                    return whitelist.isEmpty() || whitelist.contains(name);
                })
                .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", filtered.size());
            result.put("tables", filtered);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("listTables failed", e);
            return "{\"error\":\"list_tables_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "查看指定表的结构，包括列名、数据类型、索引信息。用于了解表结构后编写SQL。")
    public String describeTable(
            @ToolParam(description = "表名") String tableName) {
        try {
            if (tableName == null || tableName.isBlank()) {
                return "{\"error\":\"invalid_input\",\"message\":\"tableName 不能为空\"}";
            }
            if (!whitelistService.isAllowed(tableName)) {
                throw new McpException(McpErrorCode.TABLE_NOT_IN_WHITELIST, tableName);
            }
            Map<String, Object> description = executorService.describeTable(tableName);
            return objectMapper.writeValueAsString(description);
        } catch (McpException e) {
            return "{\"error\":\"" + e.getErrorCode().name() + "\",\"message\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("describeTable failed for: {}", tableName, e);
            return "{\"error\":\"describe_table_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "在数据库上执行只读SELECT查询。支持复杂查询、JOIN、聚合。返回查询结果JSON。需先用listTables和describeTable了解表结构。")
    public String queryData(
            @ToolParam(description = "SQL查询语句") String sql,
            @ToolParam(description = "最大返回行数，可选，默认100") int maxRows) {
        long start = System.currentTimeMillis();
        try {
            securityService.validateSql(sql, true);
            String limitedSql = securityService.enforceRowLimit(sql, maxRows);

            int effectiveLimit = maxRows > 0
                ? Math.min(maxRows, mcpProperties.getMaxRowLimit())
                : mcpProperties.getDefaultRowLimit();
            List<Map<String, Object>> rows = executorService.executeQuery(
                limitedSql, effectiveLimit, mcpProperties.getQueryTimeoutSeconds()
            );

            long elapsed = System.currentTimeMillis() - start;
            auditService.log("queryData", sql, SqlOperationType.SELECT,
                AuditStatus.SUCCESS, null, rows.size(), elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rowCount", rows.size());
            result.put("data", rows);
            result.put("executionMs", elapsed);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("queryData", sql, SqlOperationType.SELECT,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("queryData failed: {}", sql, e);
            return "{\"error\":\"query_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "在数据库执行INSERT/UPDATE/DELETE操作。仅在写操作启用时可用。需谨慎使用。")
    public String executeSql(
            @ToolParam(description = "SQL语句") String sql) {
        if (!mcpProperties.isWriteEnabled()) {
            return "{\"error\":\"write_not_enabled\",\"message\":\"写操作未启用\"}";
        }

        long start = System.currentTimeMillis();
        try {
            SqlOperationType opType = securityService.validateSql(sql, false);

            if (opType == SqlOperationType.SELECT) {
                return "{\"error\":\"use_query_tool\",\"message\":\"SELECT 请使用 queryData 工具\"}";
            }

            int affected = executorService.executeUpdate(sql, mcpProperties.getQueryTimeoutSeconds());

            long elapsed = System.currentTimeMillis() - start;
            auditService.log("executeSql", sql, opType,
                AuditStatus.SUCCESS, null, affected, elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("operationType", opType.getLabel());
            result.put("rowsAffected", affected);
            result.put("executionMs", elapsed);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.log("executeSql", sql, null,
                AuditStatus.ERROR, e.getMessage(), 0, elapsed);
            log.error("executeSql failed: {}", sql, e);
            return "{\"error\":\"execute_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
