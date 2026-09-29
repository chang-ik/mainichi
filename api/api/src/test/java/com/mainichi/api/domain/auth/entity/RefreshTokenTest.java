package com.mainichi.api.domain.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenTest {

    private RefreshToken tokenExpiringAt(LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .tokenHash("hash")
                .deviceInfo("test-agent")
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    @DisplayName("만료 전이고 폐기되지 않았다면 유효하다")
    void valid() {
        RefreshToken token = tokenExpiringAt(LocalDateTime.now().plusDays(1));

        assertTrue(token.isValid());
        assertNull(token.getRevokedAt());
    }

    @Test
    @DisplayName("만료 시각이 지나면 유효하지 않다")
    void expired() {
        RefreshToken token = tokenExpiringAt(LocalDateTime.now().minusSeconds(1));

        assertFalse(token.isValid());
    }

    @Test
    @DisplayName("폐기하면 만료 전이어도 유효하지 않다")
    void revoked() {
        RefreshToken token = tokenExpiringAt(LocalDateTime.now().plusDays(1));

        token.revoke();

        assertNotNull(token.getRevokedAt());
        assertFalse(token.isValid());
    }

    @Test
    @DisplayName("markUsed는 마지막 사용 시각을 기록한다")
    void markUsed() {
        RefreshToken token = tokenExpiringAt(LocalDateTime.now().plusDays(1));
        assertNull(token.getLastUsedAt());

        token.markUsed();

        assertNotNull(token.getLastUsedAt());
    }
}
