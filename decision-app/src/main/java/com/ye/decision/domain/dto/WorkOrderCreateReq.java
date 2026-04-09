package com.ye.decision.domain.dto;

import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkOrderCreateReq(
    @NotNull WorkOrderType type,
    WorkOrderPriority priority,
    @NotBlank @Size(max = 256) String title,
    @NotBlank String description,
    @NotBlank @Size(max = 64) String customerId,
    @Size(max = 128) String sessionId
) {}
