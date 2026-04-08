package com.ye.decision.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ye.decision.domain.enums.WorkOrderAction;

import java.time.LocalDateTime;

@TableName("work_order_log")
public class WorkOrderLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private WorkOrderAction action;
    private String operator;
    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public WorkOrderLogEntity() {}

    public WorkOrderLogEntity(String orderNo, WorkOrderAction action, String operator, String content) {
        this.orderNo = orderNo;
        this.action = action;
        this.operator = operator;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public WorkOrderAction getAction() { return action; }
    public String getOperator() { return operator; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
