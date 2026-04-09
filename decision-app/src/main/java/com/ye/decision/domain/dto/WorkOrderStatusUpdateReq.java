package com.ye.decision.domain.dto;

import com.ye.decision.domain.enums.WorkOrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkOrderStatusUpdateReq(
    @NotNull WorkOrderStatus status,
    @Size(max = 1024) String note,
    @NotBlank @Size(max = 64) String operator
) {}
