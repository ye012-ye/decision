package com.ye.decision.mcp.domain.dto;

/**
 * MCP 工具列表响应。
 *
 * @author ye
 */
public record McpToolVO(
    String name,
    String description,
    boolean enabled
) {}
