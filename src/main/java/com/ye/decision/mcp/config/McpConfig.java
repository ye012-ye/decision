package com.ye.decision.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.mcp.service.McpAuditService;
import com.ye.decision.mcp.service.McpSqlExecutorService;
import com.ye.decision.mcp.service.McpSqlSecurityService;
import com.ye.decision.mcp.service.McpWhitelistService;
import com.ye.decision.mcp.tool.DescribeTableTool;
import com.ye.decision.mcp.tool.ExecuteSqlTool;
import com.ye.decision.mcp.tool.ListTablesTool;
import com.ye.decision.mcp.tool.QueryDataTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 模块核心配置。
 * <p>
 * 职责：
 * <ul>
 *   <li>注册 MCP 数据库操作工具 Bean</li>
 *   <li>{@link ExecuteSqlTool} 仅在 {@code decision.mcp.write-enabled=true} 时装配</li>
 * </ul>
 *
 * @author ye
 */
@Configuration
@ConditionalOnProperty(name = "decision.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class McpConfig {

    @Bean
    public ListTablesTool listTablesTool(McpSqlExecutorService executorService,
                                         McpWhitelistService whitelistService,
                                         ObjectMapper objectMapper) {
        return new ListTablesTool(executorService, whitelistService, objectMapper);
    }

    @Bean
    public DescribeTableTool describeTableTool(McpSqlExecutorService executorService,
                                               McpWhitelistService whitelistService,
                                               ObjectMapper objectMapper) {
        return new DescribeTableTool(executorService, whitelistService, objectMapper);
    }

    @Bean
    public QueryDataTool queryDataTool(McpSqlSecurityService securityService,
                                       McpSqlExecutorService executorService,
                                       McpAuditService auditService,
                                       McpProperties mcpProperties,
                                       ObjectMapper objectMapper) {
        return new QueryDataTool(securityService, executorService, auditService, mcpProperties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "decision.mcp.write-enabled", havingValue = "true")
    public ExecuteSqlTool executeSqlTool(McpSqlSecurityService securityService,
                                         McpSqlExecutorService executorService,
                                         McpAuditService auditService,
                                         McpProperties mcpProperties,
                                         ObjectMapper objectMapper) {
        return new ExecuteSqlTool(securityService, executorService, auditService, mcpProperties, objectMapper);
    }
}
