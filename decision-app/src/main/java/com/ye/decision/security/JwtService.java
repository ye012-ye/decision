package com.ye.decision.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。jjwt 0.12.x API。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expireMillis = properties.expireMinutes() * 60_000L;
    }

    public String generateToken(String username) {
        return generateToken(username, System.currentTimeMillis());
    }

    /** 测试种子：允许指定签发时间，便于构造过期 token。 */
    String generateToken(String username, long issuedAtMillis) {
        return Jwts.builder()
            .subject(username)
            .issuedAt(new Date(issuedAtMillis))
            .expiration(new Date(issuedAtMillis + expireMillis))
            .signWith(key)
            .compact();
    }

    public String parseUsername(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseUsername(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
