package com.mainichi.api.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserTest {

    private User newUser() {
        return User.builder()
                .provider("kakao")
                .socialId("12345")
                .email("user@example.com")
                .build();
    }

    @Test
    @DisplayName("빌더로 생성하면 기본 레벨 0, 타임존 Asia/Seoul이 설정된다")
    void defaults() {
        User user = newUser();

        assertEquals("kakao", user.getProvider());
        assertEquals("12345", user.getSocialId());
        assertEquals("user@example.com", user.getEmail());
        assertEquals(0, user.getCurrentLevel());
        assertEquals("Asia/Seoul", user.getTimezone());
        assertNull(user.getNickname());
    }

    @Test
    @DisplayName("닉네임과 타임존을 함께 수정한다")
    void updateBoth() {
        User user = newUser();

        user.updateProfile("마이니치", "Asia/Tokyo");

        assertEquals("마이니치", user.getNickname());
        assertEquals("Asia/Tokyo", user.getTimezone());
    }

    @Test
    @DisplayName("null로 넘긴 필드는 기존 값을 유지한다")
    void updateIgnoresNull() {
        User user = newUser();
        user.updateProfile("마이니치", "Asia/Tokyo");

        user.updateProfile(null, null);

        assertEquals("마이니치", user.getNickname());
        assertEquals("Asia/Tokyo", user.getTimezone());
    }

    @Test
    @DisplayName("닉네임만 수정하면 타임존은 바뀌지 않는다")
    void updateNicknameOnly() {
        User user = newUser();

        user.updateProfile("새닉네임", null);

        assertEquals("새닉네임", user.getNickname());
        assertEquals("Asia/Seoul", user.getTimezone());
    }
}
