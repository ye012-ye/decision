package com.ye.decision.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Administrator
 */
public record QueryRedisReq(
    @JsonProperty(required = true) String keyPattern,
    @JsonProperty(required = true) String dataType   // "string" | "hash" | "zset" | "list"
) {}
