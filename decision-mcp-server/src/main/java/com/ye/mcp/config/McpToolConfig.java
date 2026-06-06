package com.ye.mcp.config;

import com.ye.mcp.tool.DatabaseTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@link DatabaseTools} 上的 @Tool 方法注册为 MCP Server 对外暴露的工具。
 * <p>
 * Spring AI MCP Server 只暴露 {@link ToolCallbackProvider} Bean 提供的工具，
 * 不会自动扫描 @Component 上的 @Tool 方法，因此必须在此显式注册，
 * 否则 tools/list 返回空，客户端（decision-app）将拿不到 listTables 等工具。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider databaseToolCallbackProvider(DatabaseTools databaseTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(databaseTools)
            .build();
    }
}
