package com.ye.decision.controller;

import com.ye.decision.mapper.ChatMessageMapper;
import com.ye.decision.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AgentService agentService;
    // MyBatis-Plus Mapper 在 @WebMvcTest 中没有 SqlSessionFactory，需要 mock
    @MockBean ChatMessageMapper chatMessageMapper;

    @Test
    void stream_returnsSseContentType() throws Exception {
        when(agentService.chat(any(), any())).thenReturn(Flux.just("hello"));

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
    void stream_emitsTokensAndDoneSignal() throws Exception {
        when(agentService.chat("s1", "hi")).thenReturn(Flux.just("Hello", " World"));

        MvcResult async = mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        async.getRequest().getAsyncContext().setTimeout(3000);

        mockMvc.perform(asyncDispatch(async))
            .andExpect(status().isOk());

        String body = async.getResponse().getContentAsString();
        assertThat(body).contains("Hello");
        assertThat(body).contains("[DONE]");
    }
}
