package com.ye.decision.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Administrator
 */
public record ApiCallReq(
    @JsonProperty(required = true) String service,  // "weather" | "logistics" | "exchange-rate"
    @JsonProperty(required = true) String params    // JSON 字符串
) {}
