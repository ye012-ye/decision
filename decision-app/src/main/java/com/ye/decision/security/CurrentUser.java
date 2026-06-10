package com.ye.decision.security;

import org.springframework.security.core.AuthenticatedPrincipal;

public record CurrentUser(
    Long id,
    String username,
    String nickname,
    String role
) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
