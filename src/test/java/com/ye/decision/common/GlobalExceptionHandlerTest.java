package com.ye.decision.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @RestController
    static class FakeController {
        @GetMapping("/fake-error")
        String boom() { throw new RuntimeException("test error"); }
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new FakeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void runtimeException_returns500WithResultBody() throws Exception {
        mockMvc.perform(get("/fake-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("test error"));
    }
}
