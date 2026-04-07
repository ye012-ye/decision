package com.ye.mcp.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record TableWhitelistReq(
    @NotBlank(message = "表名不能为空") String tableName,
    String description
) {}
