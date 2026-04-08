package com.ye.decision.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkOrderReq(
    @JsonProperty(required = true) String action,
    String orderNo,
    String type,
    String priority,
    String title,
    String description,
    String customerId,
    String status,
    String resolution,
    String note
) {}
