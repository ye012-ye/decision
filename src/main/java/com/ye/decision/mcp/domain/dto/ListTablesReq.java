package com.ye.decision.mcp.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * list_tables 工具输入。
 *
 * @author ye
 */
public record ListTablesReq(
    @JsonProperty String schemaPattern
) {}
