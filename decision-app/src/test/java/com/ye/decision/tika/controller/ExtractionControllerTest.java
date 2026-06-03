package com.ye.decision.tika.controller;

import com.ye.decision.tika.domain.ExtractorConfig;
import com.ye.decision.tika.service.TikaExtractorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link ExtractionController} Web 层测试——standalone MockMvc + 真实 service。 */
class ExtractionControllerTest {

    private ThreadPoolTaskExecutor executor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setDaemon(true);
        executor.initialize();

        var service = new TikaExtractorService(ExtractorConfig.builder().build(), executor);
        mockMvc = MockMvcBuilders.standaloneSetup(new ExtractionController(service)).build();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void upload_returnsExtractResultJson() throws Exception {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello world".getBytes());

        mockMvc.perform(multipart("/api/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("hello world")));
    }

    @Test
    void emptyFile_returnsBadRequest() throws Exception {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/extract").file(file))
                .andExpect(status().isBadRequest());
    }
}
