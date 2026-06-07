package com.ye.decision.agent.core;

/**
 * 当前登录用户上下文，由 ChatController 从 JWT/SecurityContext 提取后传入 Agent。
 */
public record UserContext(String username, String nickname, String role) {
}
