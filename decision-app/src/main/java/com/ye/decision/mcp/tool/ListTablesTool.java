package com.ye.decision.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.mcp.domain.dto.ListTablesReq;
import com.ye.decision.mcp.service.McpSqlExecutorService;
import com.ye.decision.mcp.service.McpWhitelistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * MCP 工具：列出数据库中可查询的表。
 * <p>
 * 仅返回白名单允许的表，过滤黑名单表。
 *
 * @author ye
 */
public class ListTablesTool implements Function<ListTablesReq, String> {

    private static final Logger log = LoggerFactory.getLogger(ListTablesTool.class);

    private final McpSqlExecutorService executorService;
    private final McpWhitelistService whitelistService;
    private final ObjectMapper objectMapper;

    public ListTablesTool(McpSqlExecutorService executorService,
                          McpWhitelistService whitelistService,
                          ObjectMapper objectMapper) {
        this.executorService = executorService;
        this.whitelistService = whitelistService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(ListTablesReq req) {
        try {
            List<Map<String, String>> allTables = executorService.listTables();
            Set<String> blacklist = whitelistService.getBlacklist();
            Set<String> whitelist = whitelistService.getEffectiveWhitelist();

            // 双重过滤：先排除黑名单，再检查白名单（白名单为空时不过滤）
            // 这样 Agent 只能看到被允许的表，不会暴露审计表等敏感表名
            List<Map<String, String>> filtered = allTables.stream()
                .filter(t -> {
                    String name = t.get("tableName").toLowerCase();
                    if (blacklist.contains(name)) return false;
                    return whitelist.isEmpty() || whitelist.contains(name);
                })
                .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", filtered.size());
            result.put("tables", filtered);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("ListTablesTool failed", e);
            return "{\"error\":\"list_tables_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
