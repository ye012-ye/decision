package com.ye.decision.mcp.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * describe_table 工具输入。
 *
 * @author ye
 */
public record DescribeTableReq(
    @JsonProperty(required = true) String tableName
) {}
