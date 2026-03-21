package com.ye.decision.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    // RETURNS_DEEP_STUBS 让 mock 支持链式调用
    ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    AgentService service = new AgentService(chatClient);

    @Test
    void chat_returnsFluxOfTokens() {
        when(chatClient.prompt()
            .user(any(String.class))
            .advisors(any(java.util.function.Consumer.class))
            .stream()
            .content())
            .thenReturn(Flux.just("Hello", " World"));

        Flux<String> result = service.chat("session-1", "hi");

        StepVerifier.create(result)
            .expectNext("Hello")
            .expectNext(" World")
            .verifyComplete();
    }
}
