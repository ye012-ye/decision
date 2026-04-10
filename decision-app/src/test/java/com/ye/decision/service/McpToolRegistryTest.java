package com.ye.decision.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class McpToolRegistryTest {

    @Test
    void refreshNow_keepsToolListEmptyWhenClientUnavailable() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(false);
        when(client.initialize()).thenThrow(new IllegalStateException("connection refused"));

        McpToolRegistry registry = new McpToolRegistry(List.of(client), Duration.ofSeconds(30), Duration.ofSeconds(5));

        registry.refreshNow();

        assertThat(registry.getToolCallbacks()).isEmpty();
    }

    @Test
    void refreshNow_registersToolsAfterClientBecomesAvailable() {
        McpSyncClient client = mock(McpSyncClient.class);
        McpToolRegistry registry = new McpToolRegistry(List.of(client), Duration.ofSeconds(30), Duration.ofSeconds(5));

        when(client.isInitialized()).thenReturn(false);
        when(client.initialize()).thenThrow(new IllegalStateException("down"));
        registry.refreshNow();
        assertThat(registry.getToolCallbacks()).isEmpty();

        reset(client);

        McpSchema.InitializeResult initializeResult = new McpSchema.InitializeResult(
            "2025-03-26",
            null,
            new McpSchema.Implementation("decision-mcp-server", "Decision MCP", "1.0.0"),
            null
        );
        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("mcpListTables")
            .description("列出数据库表")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), Boolean.FALSE, Map.of(), Map.of()))
            .build();

        when(client.isInitialized()).thenReturn(false);
        when(client.initialize()).thenReturn(initializeResult);
        when(client.getClientCapabilities()).thenReturn(null);
        when(client.getClientInfo()).thenReturn(new McpSchema.Implementation("decision-app", "Decision App", "1.0.0"));
        when(client.getCurrentInitializationResult()).thenReturn(initializeResult);
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));

        registry.refreshNow();

        assertThat(registry.getToolCallbacks())
            .extracting(ToolCallback::getToolDefinition)
            .extracting(definition -> definition.name())
            .containsExactly("mcpListTables");
    }
}
