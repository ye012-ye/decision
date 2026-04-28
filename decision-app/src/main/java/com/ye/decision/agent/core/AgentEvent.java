package com.ye.decision.agent.core;

public record AgentEvent(AgentEventType type, String payload) {

    public static AgentEvent route(String agentName) {
        return new AgentEvent(AgentEventType.ROUTE, agentName);
    }

    public static AgentEvent thought(String text) {
        return new AgentEvent(AgentEventType.THOUGHT, text);
    }

    public static AgentEvent action(String toolName, String arguments) {
        return new AgentEvent(AgentEventType.ACTION, toolName + " | " + arguments);
    }

    public static AgentEvent observation(String result) {
        return new AgentEvent(AgentEventType.OBSERVATION, result);
    }

    public static AgentEvent answer(String chunk) {
        return new AgentEvent(AgentEventType.ANSWER, chunk);
    }

    public static AgentEvent done() {
        return new AgentEvent(AgentEventType.DONE, "");
    }

    public static AgentEvent error(String message) {
        return new AgentEvent(AgentEventType.ERROR, message);
    }
}
