package com.ye.decision.controller;

import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentContext;
import com.ye.decision.agent.core.AgentEventType;
import com.ye.decision.agent.core.UserContext;
import com.ye.decision.domain.dto.ChatRequest;
import com.ye.decision.domain.entity.SysUser;
import com.ye.decision.mapper.SysUserMapper;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
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
    private final ExecutorService sseExecutor;
    private final SysUserMapper userMapper;

    public ChatController(Agent agent, ExecutorService sseExecutor, SysUserMapper userMapper) {
        this.agent = agent;
        this.sseExecutor = sseExecutor;
        this.userMapper = userMapper;
    }

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 在线程分叉前取出用户上下文（SecurityContext 是 ThreadLocal，不跨线程）
        UserContext userContext = extractUserContext();

        sseExecutor.execute(() -> {
            try {
                agent.chat(new AgentContext(request.sessionId(), request.message(), userContext))
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

    UserContext extractUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        SysUser user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, authentication.getName()));
        if (user == null) {
            return null;
        }
        return new UserContext(user.getUsername(),
            user.getNickname() != null ? user.getNickname() : user.getUsername(),
            user.getRole() != null ? user.getRole() : "USER");
    }

    private static String toEventName(AgentEventType type) {
        return type.name().toLowerCase();
    }
}
