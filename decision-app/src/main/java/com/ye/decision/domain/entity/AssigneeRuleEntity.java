package com.ye.decision.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ye.decision.domain.enums.WorkOrderType;

@TableName("assignee_rule")
public class AssigneeRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private WorkOrderType workOrderType;
    private String assigneeGroup;
    private String assignee;
    private String assigneeEmail;
    private Integer status;

    public AssigneeRuleEntity() {}

    public Long getId() { return id; }
    public WorkOrderType getWorkOrderType() { return workOrderType; }
    public String getAssigneeGroup() { return assigneeGroup; }
    public String getAssignee() { return assignee; }
    public String getAssigneeEmail() { return assigneeEmail; }
    public Integer getStatus() { return status; }
}
