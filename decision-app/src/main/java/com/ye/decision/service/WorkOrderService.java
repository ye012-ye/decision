package com.ye.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ye.decision.domain.dto.NotificationMessage;
import com.ye.decision.domain.entity.AssigneeRuleEntity;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.mapper.AssigneeRuleMapper;
import com.ye.decision.mapper.WorkOrderLogMapper;
import com.ye.decision.mapper.WorkOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper logMapper;
    private final AssigneeRuleMapper ruleMapper;
    private final NotificationService notificationService;

    private final AtomicInteger dailySeq = new AtomicInteger(0);
    private volatile String lastDate = "";

    public WorkOrderService(WorkOrderMapper workOrderMapper,
                            WorkOrderLogMapper logMapper,
                            AssigneeRuleMapper ruleMapper,
                            NotificationService notificationService) {
        this.workOrderMapper = workOrderMapper;
        this.logMapper = logMapper;
        this.ruleMapper = ruleMapper;
        this.notificationService = notificationService;
    }

    public WorkOrderEntity create(WorkOrderType type, WorkOrderPriority priority,
                                   String title, String description,
                                   String customerId, String sessionId) {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setOrderNo(generateOrderNo());
        entity.setType(type);
        entity.setPriority(priority != null ? priority : WorkOrderPriority.MEDIUM);
        entity.setStatus(WorkOrderStatus.PENDING);
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setCustomerId(customerId);
        entity.setSessionId(sessionId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Auto-assign
        AssigneeRuleEntity rule = ruleMapper.selectOne(
            new QueryWrapper<AssigneeRuleEntity>()
                .eq("work_order_type", type.getCode())
                .eq("status", 1)
        );
        if (rule != null) {
            entity.setAssignee(rule.getAssignee());
            entity.setAssigneeGroup(rule.getAssigneeGroup());
        }

        workOrderMapper.insert(entity);

        // Log
        logMapper.insert(new WorkOrderLogEntity(
            entity.getOrderNo(), WorkOrderAction.CREATE, "agent",
            "创建工单: " + title
        ));

        // Notify assignee
        if (rule != null && rule.getAssigneeEmail() != null) {
            notificationService.send(new NotificationMessage(
                "EMAIL", rule.getAssigneeEmail(),
                "新工单 " + entity.getOrderNo() + " 已分配给您",
                "类型：" + type.getLabel() + "，优先级：" + entity.getPriority().getLabel()
                    + "，标题：" + title + "，客户：" + customerId
            ));
        }

        log.info("Work order created: {}", entity.getOrderNo());
        return entity;
    }

    public WorkOrderEntity queryByOrderNo(String orderNo) {
        return workOrderMapper.selectOne(
            new QueryWrapper<WorkOrderEntity>().eq("order_no", orderNo)
        );
    }

    public List<WorkOrderEntity> queryByCustomerId(String customerId) {
        return workOrderMapper.selectList(
            new QueryWrapper<WorkOrderEntity>().eq("customer_id", customerId).orderByDesc("created_at")
        );
    }

    public void updateStatus(String orderNo, WorkOrderStatus newStatus, String note, String operator) {
        WorkOrderEntity entity = queryByOrderNo(orderNo);
        if (entity == null) {
            throw new IllegalArgumentException("工单不存在: " + orderNo);
        }
        entity.setStatus(newStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        if (newStatus == WorkOrderStatus.RESOLVED) {
            entity.setResolvedAt(LocalDateTime.now());
        }
        workOrderMapper.updateById(entity);

        logMapper.insert(new WorkOrderLogEntity(
            orderNo, WorkOrderAction.UPDATE_STATUS, operator,
            "状态变更为 " + newStatus.getLabel() + (note != null ? "，备注：" + note : "")
        ));
    }

    public void close(String orderNo, String resolution, String operator) {
        WorkOrderEntity entity = queryByOrderNo(orderNo);
        if (entity == null) {
            throw new IllegalArgumentException("工单不存在: " + orderNo);
        }
        entity.setStatus(WorkOrderStatus.CLOSED);
        entity.setResolution(resolution);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(entity);

        logMapper.insert(new WorkOrderLogEntity(
            orderNo, WorkOrderAction.CLOSE, operator,
            "关闭工单，处理结果：" + resolution
        ));
    }

    public List<WorkOrderLogEntity> getLogsByOrderNo(String orderNo) {
        return logMapper.selectList(
            new QueryWrapper<WorkOrderLogEntity>().eq("order_no", orderNo).orderByAsc("created_at")
        );
    }

    private String generateOrderNo() {
        String today = LocalDate.now().format(DATE_FMT);
        synchronized (this) {
            if (!today.equals(lastDate)) {
                lastDate = today;
                dailySeq.set(0);
            }
        }
        int seq = dailySeq.incrementAndGet();
        return "WO" + today + String.format("%03d", seq);
    }
}
