package com.ye.decision.controller;

import com.ye.decision.dto.ChatRequest;
import com.ye.decision.service.AgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;

/**
 * SSE（Server-Sent Events）流式聊天接口。
 * <p>
 * 推送命名事件，前端通过 addEventListener 分别监听：
 * <ul>
 *   <li>thought  — 模型推理过程</li>
 *   <li>action   — 工具调用（toolName | arguments）</li>
 *   <li>observation — 工具返回结果</li>
 *   <li>answer   — 最终回答</li>
 *   <li>done     — 结束标记</li>
 *   <li>error    — 异常信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 180_000L;
    private static final MediaType UTF8_TEXT =
        new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final AgentService agentService;
    private final ExecutorService sseExecutor;

    public ChatController(AgentService agentService, ExecutorService sseExecutor) {
        this.agentService = agentService;
        this.sseExecutor  = sseExecutor;
    }

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestBody ChatRequest request) {

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        sseExecutor.execute(() -> {
            try {
                agentService.chat(request.sessionId(), request.message())
                    .doOnNext(event -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name(event.type())
                                .data(event.content(), UTF8_TEXT));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]", UTF8_TEXT));
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(e -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"code\":500,\"msg\":\"" + e.getMessage() + "\"}", UTF8_TEXT));
                            emitter.complete();
                        } catch (Exception ex) {
                            emitter.completeWithError(ex);
                        }
                    })
                    .blockLast();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
