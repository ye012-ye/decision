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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkOrderServiceTest {

    WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
    WorkOrderLogMapper logMapper = mock(WorkOrderLogMapper.class);
    AssigneeRuleMapper ruleMapper = mock(AssigneeRuleMapper.class);
    NotificationService notificationService = mock(NotificationService.class);
    WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(workOrderMapper, logMapper, ruleMapper, notificationService);
    }

    @Test
    void create_setsOrderNoAndAutoAssigns() {
        AssigneeRuleEntity rule = new AssigneeRuleEntity();
        setField(rule, "assignee", "物流专员");
        setField(rule, "assigneeGroup", "物流组");
        setField(rule, "assigneeEmail", "logistics@example.com");
        when(ruleMapper.selectOne(any(QueryWrapper.class))).thenReturn(rule);
        when(workOrderMapper.insert(any(WorkOrderEntity.class))).thenReturn(1);
        when(logMapper.insert(any(WorkOrderLogEntity.class))).thenReturn(1);

        WorkOrderEntity result = service.create(
            WorkOrderType.LOGISTICS, WorkOrderPriority.HIGH,
            "物流延迟投诉", "客户反馈物流3天未更新", "13800001111", "session-1"
        );

        assertThat(result.getOrderNo()).startsWith("WO");
        assertThat(result.getAssignee()).isEqualTo("物流专员");
        assertThat(result.getAssigneeGroup()).isEqualTo("物流组");
        assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.PENDING);

        verify(logMapper).insert(any(WorkOrderLogEntity.class));
        verify(notificationService).send(any(NotificationMessage.class));
    }

    @Test
    void create_noRule_assigneeIsNull() {
        when(ruleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(workOrderMapper.insert(any(WorkOrderEntity.class))).thenReturn(1);
        when(logMapper.insert(any(WorkOrderLogEntity.class))).thenReturn(1);

        WorkOrderEntity result = service.create(
            WorkOrderType.OTHER, WorkOrderPriority.MEDIUM,
            "其他问题", "描述", "user-1", "session-2"
        );

        assertThat(result.getAssignee()).isNull();
        verify(notificationService, never()).send(any());
    }

    @Test
    void queryByOrderNo_returnsEntity() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        when(workOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

        WorkOrderEntity result = service.queryByOrderNo("WO20260408001");

        assertThat(result).isNotNull();
        assertThat(result.getOrderNo()).isEqualTo("WO20260408001");
    }

    @Test
    void queryByCustomerId_returnsList() {
        when(workOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new WorkOrderEntity()));

        List<WorkOrderEntity> result = service.queryByCustomerId("13800001111");

        assertThat(result).hasSize(1);
    }

    @Test
    void updateStatus_changesStatusAndLogsAction() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "status", WorkOrderStatus.PENDING);
        when(workOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);
        when(workOrderMapper.updateById(any(WorkOrderEntity.class))).thenReturn(1);
        when(logMapper.insert(any(WorkOrderLogEntity.class))).thenReturn(1);

        service.updateStatus("WO20260408001", WorkOrderStatus.PROCESSING, "开始处理", "agent");

        verify(workOrderMapper).updateById(any(WorkOrderEntity.class));
        verify(logMapper).insert(any(WorkOrderLogEntity.class));
    }

    @Test
    void close_setsResolvedStatusAndResolution() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "status", WorkOrderStatus.RESOLVED);
        when(workOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);
        when(workOrderMapper.updateById(any(WorkOrderEntity.class))).thenReturn(1);
        when(logMapper.insert(any(WorkOrderLogEntity.class))).thenReturn(1);

        service.close("WO20260408001", "已补发快递", "agent");

        ArgumentCaptor<WorkOrderEntity> captor = ArgumentCaptor.forClass(WorkOrderEntity.class);
        verify(workOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(captor.getValue().getResolution()).isEqualTo("已补发快递");
    }

    @Test
    void getLogsByOrderNo_returnsList() {
        when(logMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new WorkOrderLogEntity()));

        List<WorkOrderLogEntity> result = service.getLogsByOrderNo("WO20260408001");

        assertThat(result).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setField(Object target, String fieldName, T value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
