package com.mainichi.api.domain.auth.controller;

import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import com.mainichi.api.domain.auth.service.AuthService;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, new RefreshTokenCookieProvider(true, "Strict")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userId = UUID.randomUUID();
        user = User.builder().provider("kakao").socialId("12345").email("user@example.com").build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("POST /auth/login: 성공하면 200과 액세스 토큰, 유저 요약을 반환하고 리프레시 토큰은 쿠키로 내려준다")
    void loginSuccess() throws Exception {
        when(authService.login(eq("kakao"), eq("auth-code"), any()))
                .thenReturn(new AuthService.LoginResult("access", "refresh", 3600L, true, user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit-Agent")
                        .content("""
                                {"provider": "kakao", "code": "auth-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refreshToken", "refresh"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.provider").value("kakao"))
                .andExpect(jsonPath("$.user.currentLevel").value(0))
                .andExpect(jsonPath("$.user.timezone").value("Asia/Seoul"));

        // User-Agent 헤더가 deviceInfo로 전달된다.
        verify(authService).login("kakao", "auth-code", "JUnit-Agent");
    }

    @Test
    @DisplayName("POST /auth/login: 지원하지 않는 provider면 400 UNSUPPORTED_PROVIDER")
    void loginUnsupportedProvider() throws Exception {
        when(authService.login(eq("google"), any(), any())).thenThrow(new UnsupportedProviderException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "google", "code": "code"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_PROVIDER"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("POST /auth/login: 인가 코드가 유효하지 않으면 401 INVALID_SOCIAL_TOKEN")
    void loginInvalidSocialToken() throws Exception {
        when(authService.login(eq("kakao"), eq("bad"), any())).thenThrow(new InvalidSocialTokenException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "kakao", "code": "bad"}
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
                                {"provider": "", "code": "code"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /auth/login: code가 없으면 400")
    void loginMissingCode() throws Exception {
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
    @DisplayName("POST /auth/refresh: 쿠키의 토큰으로 갱신하고 새 리프레시 토큰은 쿠키로 내려준다")
    void refreshSuccess() throws Exception {
        when(authService.refresh(eq("old-refresh"), any()))
                .thenReturn(new AuthService.LoginResult("new-access", "new-refresh", 3600L, false, user));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(cookie().value("refreshToken", "new-refresh"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/auth"));
    }

    @Test
    @DisplayName("POST /auth/refresh: 만료/폐기된 토큰이면 401 INVALID_REFRESH_TOKEN")
    void refreshInvalidToken() throws Exception {
        when(authService.refresh(eq("expired"), any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "expired")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/refresh: 쿠키가 없으면 401 INVALID_REFRESH_TOKEN")
    void refreshMissingCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        verifyNoInteractions(authService);
    }

    // --------------------------------------------------------------- logout

    @Test
    @DisplayName("POST /auth/logout: 204를 반환하고 토큰을 폐기한 뒤 쿠키를 지운다")
    void logoutSuccess() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", "some-refresh")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));

        verify(authService).logout("some-refresh");
    }

    @Test
    @DisplayName("POST /auth/logout: 쿠키가 없어도 204를 반환하고 폐기는 시도하지 않는다")
    void logoutMissingCookie() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));

        verify(authService, never()).logout(any());
    }
}
