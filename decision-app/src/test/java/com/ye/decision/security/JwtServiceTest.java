package com.ye.decision.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService =
        new JwtService(new JwtProperties("test-secret-test-secret-test-secret-123456", 120));

    @Test
    void generateThenParse_roundTripsUsername() {
        String token = jwtService.generateToken("admin");
        assertThat(jwtService.parseUsername(token)).isEqualTo("admin");
    }

    @Test
    void isValid_returnsTrueForFreshToken() {
        assertThat(jwtService.isValid(jwtService.generateToken("admin"))).isTrue();
    }

    @Test
    void isValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateToken("admin");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_returnsFalseForGarbage() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
    }

    @Test
    void isValid_returnsFalseForExpiredToken() {
        String expired = jwtService.generateToken("admin", System.currentTimeMillis() - 121L * 60_000L);
        assertThat(jwtService.isValid(expired)).isFalse();
    }

    @Test
    void isValid_returnsFalseWhenSignedWithDifferentKey() {
        String token = jwtService.generateToken("admin");
        JwtService other =
            new JwtService(new JwtProperties("another-secret-another-secret-aaaaaa-123456", 120));
        assertThat(other.isValid(token)).isFalse();
    }
}
