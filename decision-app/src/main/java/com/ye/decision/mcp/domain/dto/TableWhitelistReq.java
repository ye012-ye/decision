package com.ye.decision.mcp.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 白名单操作请求。
 *
 * @author ye
 */
public record TableWhitelistReq(
    @NotBlank(message = "表名不能为空") String tableName,
    String description
) {}
