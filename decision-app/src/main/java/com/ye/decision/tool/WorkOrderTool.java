package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.service.WorkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkOrderTool {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderTool.class);
    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper;

    public WorkOrderTool(WorkOrderService workOrderService, ObjectMapper objectMapper) {
        this.workOrderService = workOrderService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "workOrderTool", description = "管理客服工单：创建(create)、查询(query)、更新状态(update)、关闭(close)。创建时需提供 type/title/description/customerId，会自动指派处理人并发送通知。")
    public String workOrder(
            @ToolParam(description = "操作类型: create / query / update / close") String action,
            @ToolParam(description = "工单号，query/update/close 时必填", required = false) String orderNo,
            @ToolParam(description = "工单类型: COMPLAINT / REPAIR / APPLICATION / FEEDBACK，create 时必填", required = false) String type,
            @ToolParam(description = "优先级: LOW / MEDIUM / HIGH / URGENT，create 时可选", required = false) String priority,
            @ToolParam(description = "工单标题，create 时必填", required = false) String title,
            @ToolParam(description = "工单描述，create 时必填", required = false) String description,
            @ToolParam(description = "客户ID，create/query 时使用", required = false) String customerId,
            @ToolParam(description = "目标状态: PENDING / PROCESSING / RESOLVED 等，update 时必填", required = false) String status,
            @ToolParam(description = "解决方案，close 时可填", required = false) String resolution,
            @ToolParam(description = "备注，update 时可填", required = false) String note) {
        try {
            return switch (action) {
                case "create" -> doCreate(type, priority, title, description, customerId);
                case "query"  -> doQuery(orderNo, customerId);
                case "update" -> doUpdate(orderNo, status, note);
                case "close"  -> doClose(orderNo, resolution);
                default       -> errorJson("unknown_action", "不支持的操作: " + action);
            };
        } catch (Exception e) {
            log.error("WorkOrderTool error: action={}", action, e);
            return errorJson("tool_error", e.getMessage());
        }
    }

    private String doCreate(String type, String priority, String title, String description, String customerId) throws Exception {
        if (type == null || title == null || description == null || customerId == null) {
            return errorJson("missing_field", "create 操作必须提供 type, title, description, customerId");
        }
        WorkOrderType orderType = WorkOrderType.valueOf(type);
        WorkOrderPriority orderPriority = priority != null
            ? WorkOrderPriority.valueOf(priority) : WorkOrderPriority.MEDIUM;

        WorkOrderEntity entity = workOrderService.create(orderType, orderPriority, title, description, customerId, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", entity.getOrderNo());
        result.put("type", entity.getType());
        result.put("priority", entity.getPriority());
        result.put("status", entity.getStatus());
        result.put("assignee", entity.getAssignee());
        result.put("assigneeGroup", entity.getAssigneeGroup());
        return objectMapper.writeValueAsString(result);
    }

    private String doQuery(String orderNo, String customerId) throws Exception {
        if (orderNo != null) {
            WorkOrderEntity entity = workOrderService.queryByOrderNo(orderNo);
            if (entity == null) {
                return errorJson("not_found", "工单不存在: " + orderNo);
            }
            List<WorkOrderLogEntity> logs = workOrderService.getLogsByOrderNo(orderNo);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workOrder", entityToMap(entity));
            result.put("logs", logs.stream().map(this::logToMap).toList());
            return objectMapper.writeValueAsString(result);
        } else if (customerId != null) {
            List<WorkOrderEntity> list = workOrderService.queryByCustomerId(customerId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", list.size());
            result.put("workOrders", list.stream().map(this::entityToMap).toList());
            return objectMapper.writeValueAsString(result);
        } else {
            return errorJson("missing_field", "query 操作需要提供 orderNo 或 customerId");
        }
    }

    private String doUpdate(String orderNo, String status, String note) throws Exception {
        if (orderNo == null || status == null) {
            return errorJson("missing_field", "update 操作必须提供 orderNo 和 status");
        }
        WorkOrderStatus newStatus = WorkOrderStatus.valueOf(status);
        workOrderService.updateStatus(orderNo, newStatus, note, "agent");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", orderNo);
        result.put("newStatus", newStatus);
        return objectMapper.writeValueAsString(result);
    }

    private String doClose(String orderNo, String resolution) throws Exception {
        if (orderNo == null) {
            return errorJson("missing_field", "close 操作必须提供 orderNo");
        }
        workOrderService.close(orderNo, resolution, "agent");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", orderNo);
        result.put("status", "CLOSED");
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> entityToMap(WorkOrderEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderNo", e.getOrderNo());
        map.put("type", e.getType());
        map.put("priority", e.getPriority());
        map.put("status", e.getStatus());
        map.put("title", e.getTitle());
        map.put("description", e.getDescription());
        map.put("customerId", e.getCustomerId());
        map.put("assignee", e.getAssignee());
        map.put("assigneeGroup", e.getAssigneeGroup());
        map.put("resolution", e.getResolution());
        map.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        map.put("resolvedAt", e.getResolvedAt() != null ? e.getResolvedAt().toString() : null);
        return map;
    }

    private Map<String, Object> logToMap(WorkOrderLogEntity l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", l.getAction());
        map.put("operator", l.getOperator());
        map.put("content", l.getContent());
        map.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
        return map;
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"workOrderTool\"}";
    }
}
