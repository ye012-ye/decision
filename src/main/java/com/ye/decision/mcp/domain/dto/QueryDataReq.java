package com.ye.decision.mcp.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * query_data 工具输入。
 *
 * @author ye
 */
public record QueryDataReq(
    @JsonProperty(required = true) String sql,
    @JsonProperty int maxRows
) {}
