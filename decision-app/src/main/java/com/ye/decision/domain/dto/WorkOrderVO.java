package com.ye.decision.domain.dto;

import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;

import java.time.LocalDateTime;

public record WorkOrderVO(
    String orderNo,
    WorkOrderType type,
    WorkOrderPriority priority,
    WorkOrderStatus status,
    String title,
    String description,
    String customerId,
    String assignee,
    String assigneeGroup,
    String resolution,
    String sessionId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime resolvedAt
) {
    public static WorkOrderVO from(WorkOrderEntity entity) {
        return new WorkOrderVO(
            entity.getOrderNo(),
            entity.getType(),
            entity.getPriority(),
            entity.getStatus(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getCustomerId(),
            entity.getAssignee(),
            entity.getAssigneeGroup(),
            entity.getResolution(),
            entity.getSessionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getResolvedAt()
        );
    }
}
