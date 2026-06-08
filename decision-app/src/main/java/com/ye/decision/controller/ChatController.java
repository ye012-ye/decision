package com.ye.decision.controller;

import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentContext;
import com.ye.decision.agent.core.AgentEventType;
import com.ye.decision.common.Result;
import com.ye.decision.domain.dto.ChatMessageVO;
import com.ye.decision.domain.dto.ChatRequest;
import com.ye.decision.domain.dto.ChatSessionVO;
import com.ye.decision.service.ChatHistoryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * SSE 流式聊天接口。事件名（lower-case）对应前端监听器：
 * route / thought / action / observation / answer / done / error。
 *
 * @author ye
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 180_000L;
    private static final MediaType UTF8_TEXT =
        new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final Agent agent;
    private final ChatHistoryService chatHistoryService;
    private final ExecutorService sseExecutor;

    public ChatController(Agent agent, ChatHistoryService chatHistoryService, ExecutorService sseExecutor) {
        this.agent = agent;
        this.chatHistoryService = chatHistoryService;
        this.sseExecutor = sseExecutor;
    }

    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> sessions() {
        return Result.ok(chatHistoryService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable String sessionId) {
        return Result.ok(chatHistoryService.getMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatHistoryService.deleteSession(sessionId);
        return Result.ok(null);
    }

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                agent.chat(new AgentContext(request.sessionId(), request.message()))
                    .doOnNext(event -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name(toEventName(event.type()))
                                .data(event.payload(), UTF8_TEXT));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(emitter::complete)
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

    private static String toEventName(AgentEventType type) {
        return type.name().toLowerCase();
    }
}
