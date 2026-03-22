package com.ye.decision.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Administrator
 */
public record QueryMysqlReq(
    @JsonProperty(required = true) String target,   // "order-service" | "user-service"
    @JsonProperty(required = true) String query
) {}
