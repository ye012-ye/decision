package com.ye.decision.controller;

import com.ye.decision.config.ThreadPoolConfig;
import com.ye.decision.domain.dto.ReActEvent;
import com.ye.decision.mapper.AssigneeRuleMapper;
import com.ye.decision.mapper.ChatMessageMapper;
import com.ye.decision.mapper.WorkOrderLogMapper;
import com.ye.decision.mapper.WorkOrderMapper;
import com.ye.decision.rag.mapper.KnowledgeBaseMapper;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import com.ye.decision.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import(ThreadPoolConfig.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AgentService agentService;
    @MockBean ChatMessageMapper chatMessageMapper;
    @MockBean AssigneeRuleMapper assigneeRuleMapper;
    @MockBean WorkOrderMapper workOrderMapper;
    @MockBean WorkOrderLogMapper workOrderLogMapper;
    @MockBean KnowledgeBaseMapper knowledgeBaseMapper;
    @MockBean KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Test
    void stream_returnsSseContentType() throws Exception {
        when(agentService.chat(any(), any()))
            .thenReturn(Flux.just(ReActEvent.answer("hello")));

        MvcResult async = mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(async))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void stream_emitsReActEventsAndDoneSignal() throws Exception {
        when(agentService.chat("s1", "查询订单")).thenReturn(Flux.just(
            ReActEvent.thought("需要查询订单数据"),
            ReActEvent.action("queryMysqlTool", "{\"target\":\"order-service\",\"query\":\"SELECT * FROM orders\"}"),
            ReActEvent.observation("{\"data\":[]}"),
            ReActEvent.answer("未找到相关订单")
        ));

        MvcResult async = mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"查询订单\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        async.getRequest().getAsyncContext().setTimeout(3000);

        mockMvc.perform(asyncDispatch(async))
            .andExpect(status().isOk());

        String body = async.getResponse().getContentAsString();
        assertThat(body).contains("event:thought");
        assertThat(body).contains("event:action");
        assertThat(body).contains("event:observation");
        assertThat(body).contains("event:answer");
        assertThat(body).contains("event:done");
    }
}
