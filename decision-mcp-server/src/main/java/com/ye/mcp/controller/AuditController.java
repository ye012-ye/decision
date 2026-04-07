package com.ye.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ye.mcp.common.Result;
import com.ye.mcp.domain.dto.AuditLogVO;
import com.ye.mcp.service.AuditService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mcp")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit-logs")
    public Result<IPage<AuditLogVO>> queryAuditLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String toolName,
        @RequestParam(required = false) String status
    ) {
        return Result.ok(auditService.queryLogs(page, size, toolName, status));
    }
}
