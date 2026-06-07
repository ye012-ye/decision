package com.ye.decision.agent.core;

public record AgentContext(String sessionId, String userMessage, UserContext userContext) {

    /** 向后兼容：不传用户上下文时 userContext 为 null。 */
    public AgentContext(String sessionId, String userMessage) {
        this(sessionId, userMessage, null);
    }
}
