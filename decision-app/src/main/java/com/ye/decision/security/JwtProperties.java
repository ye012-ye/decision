package com.ye.decision.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置：decision.security.jwt.*
 * secret 至少 32 字符（HMAC-SHA256 需要 256 位密钥）。
 */
@ConfigurationProperties(prefix = "decision.security.jwt")
public record JwtProperties(String secret, long expireMinutes) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = "decision-default-dev-secret-decision-default-dev-secret";
        }
        if (expireMinutes <= 0) {
            expireMinutes = 120;
        }
    }
}
