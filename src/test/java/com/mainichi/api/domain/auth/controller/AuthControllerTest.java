package com.mainichi.api.domain.auth.controller;

import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import com.mainichi.api.domain.auth.service.AuthService;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userId = UUID.randomUUID();
        user = User.builder().provider("kakao").socialId("12345").email("user@example.com").build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("POST /auth/login: 성공하면 200과 토큰, 유저 요약을 반환한다")
    void loginSuccess() throws Exception {
        when(authService.login(eq("kakao"), eq("kakao-token"), any()))
                .thenReturn(new AuthService.LoginResult("access", "refresh", 3600L, true, user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit-Agent")
                        .content("""
                                {"provider": "kakao", "socialToken": "kakao-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.provider").value("kakao"))
                .andExpect(jsonPath("$.user.currentLevel").value(0))
                .andExpect(jsonPath("$.user.timezone").value("Asia/Seoul"));

        // User-Agent 헤더가 deviceInfo로 전달된다.
        verify(authService).login("kakao", "kakao-token", "JUnit-Agent");
    }

    @Test
    @DisplayName("POST /auth/login: 지원하지 않는 provider면 400 UNSUPPORTED_PROVIDER")
    void loginUnsupportedProvider() throws Exception {
        when(authService.login(eq("google"), any(), any())).thenThrow(new UnsupportedProviderException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "google", "socialToken": "token"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_PROVIDER"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("POST /auth/login: 소셜 토큰이 유효하지 않으면 401 INVALID_SOCIAL_TOKEN")
    void loginInvalidSocialToken() throws Exception {
        when(authService.login(eq("kakao"), eq("bad"), any())).thenThrow(new InvalidSocialTokenException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao", "socialToken": "bad"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SOCIAL_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/login: provider가 비어 있으면 400")
    void loginBlankProvider() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "", "socialToken": "token"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /auth/login: socialToken이 없으면 400")
    void loginMissingSocialToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // -------------------------------------------------------------- refresh

    @Test
    @DisplayName("POST /auth/refresh: 성공하면 새 토큰을 반환한다")
    void refreshSuccess() throws Exception {
        when(authService.refresh(eq("old-refresh"), any()))
                .thenReturn(new AuthService.LoginResult("new-access", "new-refresh", 3600L, false, user));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("POST /auth/refresh: 만료/폐기된 토큰이면 401 INVALID_REFRESH_TOKEN")
    void refreshInvalidToken() throws Exception {
        when(authService.refresh(eq("expired"), any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "expired"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/refresh: refreshToken이 비어 있으면 400")
    void refreshBlankToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": " "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // --------------------------------------------------------------- logout

    @Test
    @DisplayName("POST /auth/logout: 204를 반환하고 토큰을 폐기 요청한다")
    void logoutSuccess() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "some-refresh"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).logout("some-refresh");
    }

    @Test
    @DisplayName("POST /auth/logout: refreshToken이 없으면 400")
    void logoutMissingToken() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).logout(any());
    }
}
