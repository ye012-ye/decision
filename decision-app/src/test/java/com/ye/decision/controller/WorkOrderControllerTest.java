package com.ye.decision.controller;

import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.WorkOrderAction;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;
import com.ye.decision.mapper.AssigneeRuleMapper;
import com.ye.decision.mapper.ChatMessageMapper;
import com.ye.decision.rag.mapper.KnowledgeBaseMapper;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import com.ye.decision.mapper.WorkOrderLogMapper;
import com.ye.decision.mapper.WorkOrderMapper;
import com.ye.decision.service.WorkOrderService;
import com.ye.decision.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderController.class)
class WorkOrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WorkOrderService workOrderService;

    @MockBean
    AssigneeRuleMapper assigneeRuleMapper;

    @MockBean
    WorkOrderMapper workOrderMapper;

    @MockBean
    WorkOrderLogMapper workOrderLogMapper;

    @MockBean
    NotificationService notificationService;

    @MockBean
    ChatMessageMapper chatMessageMapper;

    @MockBean
    KnowledgeBaseMapper knowledgeBaseMapper;

    @MockBean
    KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Test
    void create_returnsCreatedTicket() throws Exception {
        when(workOrderService.create(WorkOrderType.LOGISTICS, WorkOrderPriority.HIGH, "物流延迟", "三天未更新", "13800001111", "session-1"))
            .thenReturn(buildEntity(WorkOrderStatus.PENDING));

        mockMvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type":"LOGISTICS",
                      "priority":"HIGH",
                      "title":"物流延迟",
                      "description":"三天未更新",
                      "customerId":"13800001111",
                      "sessionId":"session-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("WO20260409001"));
    }

    @Test
    void getByOrderNo_returnsWorkOrder() throws Exception {
        when(workOrderService.queryByOrderNo("WO20260409001")).thenReturn(buildEntity(WorkOrderStatus.PENDING));

        mockMvc.perform(get("/api/work-orders/WO20260409001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("WO20260409001"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getByOrderNo_notFound_returnsBadRequestResult() throws Exception {
        when(workOrderService.queryByOrderNo("WO20260409001")).thenReturn(null);

        mockMvc.perform(get("/api/work-orders/WO20260409001"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("工单不存在: WO20260409001"));
    }

    @Test
    void logs_returnsOperationHistory() throws Exception {
        when(workOrderService.getLogsByOrderNo("WO20260409001"))
            .thenReturn(List.of(new WorkOrderLogEntity("WO20260409001", WorkOrderAction.CREATE, "agent", "创建工单")));

        mockMvc.perform(get("/api/work-orders/WO20260409001/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].action").value("CREATE"));
    }

    @Test
    void updateStatus_returnsUpdatedTicket() throws Exception {
        doNothing().when(workOrderService).updateStatus("WO20260409001", WorkOrderStatus.PROCESSING, "开始处理", "agent");
        when(workOrderService.queryByOrderNo("WO20260409001")).thenReturn(buildEntity(WorkOrderStatus.PROCESSING));

        mockMvc.perform(patch("/api/work-orders/WO20260409001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status":"PROCESSING",
                      "note":"开始处理",
                      "operator":"agent"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("WO20260409001"))
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void close_returnsClosedTicket() throws Exception {
        doNothing().when(workOrderService).close("WO20260409001", "已处理", "agent");
        when(workOrderService.queryByOrderNo("WO20260409001")).thenReturn(buildEntity(WorkOrderStatus.CLOSED));

        mockMvc.perform(post("/api/work-orders/WO20260409001/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "resolution":"已处理",
                      "operator":"agent"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("WO20260409001"))
            .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void list_bindsQueryParamsIntoServiceCall() throws Exception {
        when(workOrderService.list("WO20260409001", "13800001111", WorkOrderStatus.PROCESSING, WorkOrderType.LOGISTICS, WorkOrderPriority.HIGH))
            .thenReturn(List.of(buildEntity(WorkOrderStatus.PROCESSING)));

        mockMvc.perform(get("/api/work-orders")
                .param("orderNo", "WO20260409001")
                .param("customerId", "13800001111")
                .param("status", "PROCESSING")
                .param("type", "LOGISTICS")
                .param("priority", "HIGH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].orderNo").value("WO20260409001"));

        verify(workOrderService).list("WO20260409001", "13800001111", WorkOrderStatus.PROCESSING, WorkOrderType.LOGISTICS, WorkOrderPriority.HIGH);
    }

    private static WorkOrderEntity buildEntity(WorkOrderStatus status) {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setOrderNo("WO20260409001");
        entity.setType(WorkOrderType.LOGISTICS);
        entity.setPriority(WorkOrderPriority.HIGH);
        entity.setStatus(status);
        entity.setTitle("物流延迟");
        entity.setDescription("三天未更新");
        entity.setCustomerId("13800001111");
        entity.setAssignee("物流专员");
        entity.setAssigneeGroup("物流组");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
