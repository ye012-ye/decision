package com.ye.decision.domain.dto;

import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.WorkOrderAction;

import java.time.LocalDateTime;

public record WorkOrderLogVO(
    WorkOrderAction action,
    String operator,
    String content,
    LocalDateTime createdAt
) {
    public static WorkOrderLogVO from(WorkOrderLogEntity entity) {
        return new WorkOrderLogVO(
            entity.getAction(),
            entity.getOperator(),
            entity.getContent(),
            entity.getCreatedAt()
        );
    }
}
