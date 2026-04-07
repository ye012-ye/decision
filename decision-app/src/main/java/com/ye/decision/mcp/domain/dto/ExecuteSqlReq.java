package com.ye.decision.mcp.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * execute_sql 工具输入。
 *
 * @author ye
 */
public record ExecuteSqlReq(
    @JsonProperty(required = true) String sql
) {}
