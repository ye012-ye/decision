package com.ye.decision.agent.core;

import reactor.core.publisher.Flux;

public interface Agent {
    Flux<AgentEvent> chat(AgentContext context);
}
