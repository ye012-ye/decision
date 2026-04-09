package com.ye.decision.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkOrderCloseReq(
    @NotBlank String resolution,
    @NotBlank @Size(max = 64) String operator
) {}
