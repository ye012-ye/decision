package com.ye.decision.mcp.controller;

import com.ye.decision.common.Result;
import com.ye.decision.mcp.domain.dto.TableWhitelistReq;
import com.ye.decision.mcp.domain.entity.McpWhitelistEntity;
import com.ye.decision.mcp.service.McpWhitelistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 表白名单管理接口。
 *
 * @author ye
 */
@RestController
@RequestMapping("/api/mcp/whitelist")
public class McpWhitelistController {

    private final McpWhitelistService whitelistService;

    public McpWhitelistController(McpWhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @GetMapping
    public Result<List<McpWhitelistEntity>> list() {
        return Result.ok(whitelistService.listAll());
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody TableWhitelistReq req) {
        whitelistService.addTable(req);
        return Result.ok(null);
    }

    @DeleteMapping("/{tableName}")
    public Result<Void> remove(@PathVariable String tableName) {
        whitelistService.removeTable(tableName);
        return Result.ok(null);
    }
}
