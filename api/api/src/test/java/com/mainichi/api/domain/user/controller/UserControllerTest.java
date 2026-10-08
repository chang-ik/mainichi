package com.mainichi.api.domain.user.controller;

import com.mainichi.api.domain.auth.controller.RefreshTokenCookieProvider;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.exception.UserNotFoundException;
import com.mainichi.api.domain.user.service.UserService;
import com.mainichi.api.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(userService, new RefreshTokenCookieProvider(true, "Strict")))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userId = UUID.randomUUID();
        user = User.builder().provider("kakao").socialId("12345").email("user@example.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        // JwtAuthenticationFilter가 하는 것처럼 principal에 userId를 넣는다.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /user/me: 내 프로필을 반환한다")
    void me() throws Exception {
        when(userService.getById(userId)).thenReturn(user);

        mockMvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.provider").value("kakao"))
                .andExpect(jsonPath("$.currentLevel").value(0))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));
    }

    @Test
    @DisplayName("GET /user/me: 탈퇴 등으로 유저가 없으면 404 USER_NOT_FOUND")
    void meUserNotFound() throws Exception {
        when(userService.getById(userId)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/user/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /user/me: 본문이 JSON 이 아니면 400 INVALID_REQUEST")
    void updateMeMalformedBody() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nickname"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /user/me: 프로필을 수정하고 수정된 결과를 반환한다")
    void updateMe() throws Exception {
        when(userService.getById(userId)).thenReturn(user);
        // 서비스 대신 실제 엔티티 동작을 재현한다.
        org.mockito.Mockito.doAnswer(inv -> {
            user.updateProfile(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
            return null;
        }).when(userService).updateProfile(any(), any(), any(), any(), any());

        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "마이니치", "timezone": "Asia/Tokyo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("마이니치"))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        verify(userService).updateProfile(userId, "마이니치", "Asia/Tokyo", null, null);
    }

    @Test
    @DisplayName("PATCH /user/me: 닉네임이 2자 미만이면 400")
    void updateMeNicknameTooShort() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /user/me: 닉네임이 20자를 넘으면 400")
    void updateMeNicknameTooLong() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"" + "a".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /user/me: 학습 단계를 바꾸면 바뀐 단계를 반환한다")
    void updateMeLevel() throws Exception {
        when(userService.getById(userId)).thenReturn(user);
        org.mockito.Mockito.doAnswer(inv -> {
            user.updateProfile(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
            return null;
        }).when(userService).updateProfile(any(), any(), any(), any(), any());

        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentLevel\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLevel").value(3));

        verify(userService).updateProfile(userId, null, null, 3, null);
    }

    @Test
    @DisplayName("PATCH /user/me: 학습 단계가 0~5 밖이면 400")
    void updateMeLevelOutOfRange() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentLevel\": 6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /user/me: 학습 영역을 바꾸면 단어를 포함한 영역 목록을 반환한다")
    void updateMeStudyAreas() throws Exception {
        when(userService.getById(userId)).thenReturn(user);
        org.mockito.Mockito.doAnswer(inv -> {
            user.changeStudyAreas(inv.getArgument(4));
            return null;
        }).when(userService).updateProfile(any(), any(), any(), any(), any());

        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyAreas\": [\"LISTENING\", \"GRAMMAR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyAreas[0]").value("WORD"))
                .andExpect(jsonPath("$.studyAreas[1]").value("GRAMMAR"))
                .andExpect(jsonPath("$.studyAreas[2]").value("LISTENING"));
    }

    @Test
    @DisplayName("PATCH /user/me: 없는 학습 영역이면 400")
    void updateMeUnknownStudyArea() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyAreas\": [\"MUSIC\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("DELETE /user/me: 204를 반환하고 계정을 삭제하며 리프레시 토큰 쿠키를 지운다")
    void deleteMe() throws Exception {
        mockMvc.perform(delete("/user/me"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/auth")));

        verify(userService).deleteAccount(userId);
    }
}
