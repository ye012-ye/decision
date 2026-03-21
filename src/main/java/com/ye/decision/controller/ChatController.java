package com.ye.decision.controller;

import com.ye.decision.dto.ChatRequest;
import com.ye.decision.service.AgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final AgentService agentService;
    // Java 21 虚拟线程：高并发 SSE 场景下避免阻塞平台线程
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            try {
                agentService.chat(request.sessionId(), request.message())
                    .doOnNext(token -> {
                        try { emitter.send(token); } catch (Exception e) { emitter.completeWithError(e); }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send("[DONE]");
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(e -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"code\":500,\"msg\":\"" + e.getMessage() + "\",\"data\":null}"));
                            emitter.complete();
                        } catch (Exception ex) {
                            emitter.completeWithError(ex);
                        }
                    })
                    .blockLast(); // 在虚拟线程中阻塞等待完成，不占用平台线程
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
