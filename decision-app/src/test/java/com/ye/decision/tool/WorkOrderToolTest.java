package com.ye.decision.tool;

import com.ye.decision.domain.dto.WorkOrderReq;
import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.*;
import com.ye.decision.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkOrderToolTest {

    WorkOrderService workOrderService = mock(WorkOrderService.class);
    WorkOrderTool tool;

    @BeforeEach
    void setUp() {
        tool = new WorkOrderTool(workOrderService);
    }

    @Test
    void create_returnsSuccessJson() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "type", WorkOrderType.LOGISTICS);
        setField(entity, "priority", WorkOrderPriority.HIGH);
        setField(entity, "status", WorkOrderStatus.PENDING);
        setField(entity, "assignee", "物流专员");
        setField(entity, "assigneeGroup", "物流组");
        when(workOrderService.create(any(), any(), any(), any(), any(), any())).thenReturn(entity);

        String result = tool.apply(new WorkOrderReq(
            "create", null, "LOGISTICS", "HIGH",
            "物流延迟", "快递3天没动", "13800001111",
            null, null, null
        ));

        assertThat(result).contains("WO20260408001").contains("success");
    }

    @Test
    void query_byOrderNo_returnsWorkOrder() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        setField(entity, "status", WorkOrderStatus.PENDING);
        when(workOrderService.queryByOrderNo("WO20260408001")).thenReturn(entity);
        when(workOrderService.getLogsByOrderNo("WO20260408001")).thenReturn(List.of());

        String result = tool.apply(new WorkOrderReq(
            "query", "WO20260408001", null, null,
            null, null, null, null, null, null
        ));

        assertThat(result).contains("WO20260408001");
    }

    @Test
    void query_byCustomerId_returnsList() {
        WorkOrderEntity entity = new WorkOrderEntity();
        setField(entity, "orderNo", "WO20260408001");
        when(workOrderService.queryByCustomerId("13800001111")).thenReturn(List.of(entity));

        String result = tool.apply(new WorkOrderReq(
            "query", null, null, null,
            null, null, "13800001111", null, null, null
        ));

        assertThat(result).contains("WO20260408001");
    }

    @Test
    void update_callsUpdateStatus() {
        String result = tool.apply(new WorkOrderReq(
            "update", "WO20260408001", null, null,
            null, null, null, "PROCESSING", null, "开始处理"
        ));

        verify(workOrderService).updateStatus("WO20260408001", WorkOrderStatus.PROCESSING, "开始处理", "agent");
        assertThat(result).contains("success");
    }

    @Test
    void close_callsClose() {
        String result = tool.apply(new WorkOrderReq(
            "close", "WO20260408001", null, null,
            null, null, null, null, "已补发", null
        ));

        verify(workOrderService).close("WO20260408001", "已补发", "agent");
        assertThat(result).contains("success");
    }

    @Test
    void unknownAction_returnsError() {
        String result = tool.apply(new WorkOrderReq(
            "unknown", null, null, null,
            null, null, null, null, null, null
        ));

        assertThat(result).contains("error").contains("unknown_action");
    }

    @Test
    void create_missingRequiredFields_returnsError() {
        String result = tool.apply(new WorkOrderReq(
            "create", null, null, null,
            null, null, null, null, null, null
        ));

        assertThat(result).contains("error").contains("missing_field");
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
