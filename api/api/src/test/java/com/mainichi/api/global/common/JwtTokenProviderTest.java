package com.mainichi.api.global.common;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";

    @Test
    @DisplayName("발급한 액세스 토큰에서 동일한 userId를 꺼낼 수 있다")
    void createAndParse() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);
        UUID userId = UUID.randomUUID();

        String token = provider.createAccessToken(userId);

        assertEquals(userId, provider.getUserId(token));
    }

    @Test
    @DisplayName("설정한 유효시간을 그대로 반환한다")
    void validitySeconds() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 1234);

        assertEquals(1234, provider.getAccessTokenValiditySeconds());
    }

    @Test
    @DisplayName("만료된 토큰은 파싱에 실패한다")
    void expiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -10);
        String token = provider.createAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> provider.getUserId(token));
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰은 거부한다")
    void wrongSignature() {
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 3600);
        JwtTokenProvider other = new JwtTokenProvider("another-secret-key-for-jwt-must-be-32-bytes!!", 3600);
        String token = issuer.createAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> other.getUserId(token));
    }

    @Test
    @DisplayName("형식이 잘못된 토큰은 거부한다")
    void malformedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);

        assertThrows(JwtException.class, () -> provider.getUserId("not-a-jwt"));
    }

    @Test
    @DisplayName("payload가 변조된 토큰은 거부한다")
    void tamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);
        String token = provider.createAccessToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        String other = provider.createAccessToken(UUID.randomUUID()).split("\\.")[1];
        String tampered = parts[0] + "." + other + "." + parts[2];

        assertNotEquals(token, tampered);
        assertThrows(JwtException.class, () -> provider.getUserId(tampered));
    }
}
