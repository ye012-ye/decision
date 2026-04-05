package com.ye.decision.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.mcp.domain.dto.DescribeTableReq;
import com.ye.decision.mcp.exception.McpErrorCode;
import com.ye.decision.mcp.exception.McpException;
import com.ye.decision.mcp.service.McpSqlExecutorService;
import com.ye.decision.mcp.service.McpWhitelistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具：查看指定表的结构（列、类型、索引）。
 *
 * @author ye
 */
public class DescribeTableTool implements Function<DescribeTableReq, String> {

    private static final Logger log = LoggerFactory.getLogger(DescribeTableTool.class);

    private final McpSqlExecutorService executorService;
    private final McpWhitelistService whitelistService;
    private final ObjectMapper objectMapper;

    public DescribeTableTool(McpSqlExecutorService executorService,
                             McpWhitelistService whitelistService,
                             ObjectMapper objectMapper) {
        this.executorService = executorService;
        this.whitelistService = whitelistService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(DescribeTableReq req) {
        try {
            if (req.tableName() == null || req.tableName().isBlank()) {
                return "{\"error\":\"invalid_input\",\"message\":\"tableName 不能为空\"}";
            }

            if (!whitelistService.isAllowed(req.tableName())) {
                throw new McpException(McpErrorCode.TABLE_NOT_IN_WHITELIST, req.tableName());
            }

            Map<String, Object> description = executorService.describeTable(req.tableName());
            return objectMapper.writeValueAsString(description);
        } catch (McpException e) {
            return "{\"error\":\"" + e.getErrorCode().name() + "\",\"message\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("DescribeTableTool failed for table: {}", req.tableName(), e);
            return "{\"error\":\"describe_table_failed\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
