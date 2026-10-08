package com.mainichi.api.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

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

    @Test
    @DisplayName("학습 영역은 기본으로 단어만 있고, 단어는 빼려고 해도 항상 포함된다")
    void studyAreas() {
        User user = newUser();
        assertEquals(Set.of(StudyArea.WORD), user.getStudyAreas());

        user.changeStudyAreas(Set.of(StudyArea.GRAMMAR, StudyArea.LISTENING));
        assertEquals(Set.of(StudyArea.WORD, StudyArea.GRAMMAR, StudyArea.LISTENING), user.getStudyAreas());

        user.changeStudyAreas(Set.of());
        assertEquals(Set.of(StudyArea.WORD), user.getStudyAreas());
    }

    @Test
    @DisplayName("학습 영역은 콤마 문자열로 저장되고 그대로 복원된다 (단어는 저장하지 않음)")
    void studyAreaConverter() {
        StudyAreaSetConverter converter = new StudyAreaSetConverter();

        assertEquals("GRAMMAR,LISTENING", converter.convertToDatabaseColumn(Set.of(StudyArea.LISTENING, StudyArea.GRAMMAR)));
        assertEquals("", converter.convertToDatabaseColumn(Set.of()));
        assertEquals(Set.of(StudyArea.GRAMMAR, StudyArea.LISTENING), converter.convertToEntityAttribute("GRAMMAR,LISTENING"));
        assertEquals(Set.of(), converter.convertToEntityAttribute(null));
        // 예전에 저장된, 지금은 없는 영역(대화)은 건너뛴다
        assertEquals(Set.of(StudyArea.GRAMMAR), converter.convertToEntityAttribute("GRAMMAR,CONVERSATION"));
    }
}
