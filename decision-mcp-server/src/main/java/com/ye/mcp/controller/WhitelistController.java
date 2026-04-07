package com.ye.mcp.controller;

import com.ye.mcp.common.Result;
import com.ye.mcp.domain.dto.TableWhitelistReq;
import com.ye.mcp.domain.entity.WhitelistEntity;
import com.ye.mcp.service.WhitelistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author ye
 *
 * 白名单管理
 */
@RestController
@RequestMapping("/api/mcp/whitelist")
public class WhitelistController {

    private final WhitelistService whitelistService;

    public WhitelistController(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @GetMapping
    public Result<List<WhitelistEntity>> list() {
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
