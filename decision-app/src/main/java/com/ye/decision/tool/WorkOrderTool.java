package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.WorkOrderReq;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.service.WorkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WorkOrderTool implements Function<WorkOrderReq, String> {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderTool.class);
    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkOrderTool(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @Override
    public String apply(WorkOrderReq req) {
        try {
            return switch (req.action()) {
                case "create" -> doCreate(req);
                case "query"  -> doQuery(req);
                case "update" -> doUpdate(req);
                case "close"  -> doClose(req);
                default       -> errorJson("unknown_action", "不支持的操作: " + req.action());
            };
        } catch (Exception e) {
            log.error("WorkOrderTool error: action={}", req.action(), e);
            return errorJson("tool_error", e.getMessage());
        }
    }

    private String doCreate(WorkOrderReq req) throws Exception {
        if (req.type() == null || req.title() == null || req.description() == null || req.customerId() == null) {
            return errorJson("missing_field", "create 操作必须提供 type, title, description, customerId");
        }
        WorkOrderType type = WorkOrderType.valueOf(req.type());
        WorkOrderPriority priority = req.priority() != null
            ? WorkOrderPriority.valueOf(req.priority()) : WorkOrderPriority.MEDIUM;

        WorkOrderEntity entity = workOrderService.create(type, priority, req.title(), req.description(), req.customerId(), null);

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

    private String doQuery(WorkOrderReq req) throws Exception {
        if (req.orderNo() != null) {
            WorkOrderEntity entity = workOrderService.queryByOrderNo(req.orderNo());
            if (entity == null) {
                return errorJson("not_found", "工单不存在: " + req.orderNo());
            }
            List<WorkOrderLogEntity> logs = workOrderService.getLogsByOrderNo(req.orderNo());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workOrder", entityToMap(entity));
            result.put("logs", logs.stream().map(this::logToMap).toList());
            return objectMapper.writeValueAsString(result);
        } else if (req.customerId() != null) {
            List<WorkOrderEntity> list = workOrderService.queryByCustomerId(req.customerId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", list.size());
            result.put("workOrders", list.stream().map(this::entityToMap).toList());
            return objectMapper.writeValueAsString(result);
        } else {
            return errorJson("missing_field", "query 操作需要提供 orderNo 或 customerId");
        }
    }

    private String doUpdate(WorkOrderReq req) throws Exception {
        if (req.orderNo() == null || req.status() == null) {
            return errorJson("missing_field", "update 操作必须提供 orderNo 和 status");
        }
        WorkOrderStatus newStatus = WorkOrderStatus.valueOf(req.status());
        workOrderService.updateStatus(req.orderNo(), newStatus, req.note(), "agent");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", req.orderNo());
        result.put("newStatus", newStatus);
        return objectMapper.writeValueAsString(result);
    }

    private String doClose(WorkOrderReq req) throws Exception {
        if (req.orderNo() == null) {
            return errorJson("missing_field", "close 操作必须提供 orderNo");
        }
        workOrderService.close(req.orderNo(), req.resolution(), "agent");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", req.orderNo());
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
