package com.ye.decision.common;

import com.ye.decision.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class UserContext {

        public static Optional<CurrentUser> getCurrentUser() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null){
                return Optional.empty();
            }
            return Optional.ofNullable((CurrentUser) authentication.getPrincipal());
        }
}
