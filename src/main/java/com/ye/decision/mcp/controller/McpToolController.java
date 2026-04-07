package com.ye.decision.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ye.decision.common.Result;
import com.ye.decision.mcp.domain.dto.McpAuditLogVO;
import com.ye.decision.mcp.domain.dto.McpToolVO;
import com.ye.decision.mcp.service.McpAuditService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 管理接口：工具列表、审计日志查询。
 *
 * @author ye
 */
@RestController
@RequestMapping("/api/mcp")
public class McpToolController {

    private final List<ToolCallback> toolCallbacks;
    private final McpAuditService auditService;

    public McpToolController(List<ToolCallback> toolCallbacks, McpAuditService auditService) {
        this.toolCallbacks = toolCallbacks;
        this.auditService = auditService;
    }

    /**
     * 获取所有 MCP 工具列表。
     */
    @GetMapping("/tools")
    public Result<List<McpToolVO>> listTools() {
        List<McpToolVO> mcpTools = toolCallbacks.stream()
            .filter(tc -> tc.getToolDefinition().name().startsWith("mcp"))
            .map(tc -> new McpToolVO(
                tc.getToolDefinition().name(),
                tc.getToolDefinition().description(),
                true
            ))
            .toList();
        return Result.ok(mcpTools);
    }

    /**
     * 查询审计日志。
     */
    @GetMapping("/audit-logs")
    public Result<IPage<McpAuditLogVO>> queryAuditLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String toolName,
        @RequestParam(required = false) String status
    ) {
        return Result.ok(auditService.queryLogs(page, size, toolName, status));
    }
}
