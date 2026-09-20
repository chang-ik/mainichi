package com.mainichi.api.domain.auth.service;

import com.mainichi.api.domain.auth.entity.RefreshToken;
import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import com.mainichi.api.domain.auth.repository.RefreshTokenRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import com.mainichi.api.global.common.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private KakaoOAuthService kakaoOAuthService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().provider("kakao").socialId("12345").email("user@example.com").build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    /** AuthService.hash()와 동일한 방식(SHA-256 + Base64)으로 해시를 계산한다. */
    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(value.getBytes()));
    }

    private RefreshToken storedToken(String raw, LocalDateTime expiresAt) throws Exception {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .deviceInfo("old-device")
                .expiresAt(expiresAt)
                .build();
    }

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("login: kakao 이외의 provider는 UnsupportedProviderException")
    void loginUnsupportedProvider() {
        assertThrows(UnsupportedProviderException.class,
                () -> authService.login("google", "token", "device"));

        verifyNoInteractions(kakaoOAuthService, userService, refreshTokenRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("login: 신규 유저면 토큰을 발급하고 refresh token을 해시로 저장한다")
    void loginNewUser() throws Exception {
        when(kakaoOAuthService.fetchProfile("kakao-token"))
                .thenReturn(new KakaoOAuthService.KakaoProfile("12345", "user@example.com"));
        when(userService.findOrCreateBySocialId("kakao", "12345", "user@example.com"))
                .thenReturn(new UserService.FindOrCreateResult(user, true));
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

        AuthService.LoginResult result = authService.login("kakao", "kakao-token", "Mozilla/5.0");

        assertEquals("access-token", result.accessToken());
        assertEquals(3600L, result.expiresIn());
        assertTrue(result.isNewUser());
        assertSame(user, result.user());
        assertNotNull(result.refreshToken());
        assertFalse(result.refreshToken().isBlank());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals("Mozilla/5.0", saved.getDeviceInfo());
        // DB에는 원문이 아니라 해시만 저장된다.
        assertNotEquals(result.refreshToken(), saved.getTokenHash());
        assertEquals(sha256(result.refreshToken()), saved.getTokenHash());
        // 유효기간은 30일.
        LocalDateTime expected = LocalDateTime.now().plusDays(30);
        assertTrue(ChronoUnit.SECONDS.between(saved.getExpiresAt(), expected) < 5);
        assertTrue(saved.isValid());
    }

    @Test
    @DisplayName("login: 기존 유저면 isNewUser가 false")
    void loginExistingUser() {
        when(kakaoOAuthService.fetchProfile("kakao-token"))
                .thenReturn(new KakaoOAuthService.KakaoProfile("12345", "user@example.com"));
        when(userService.findOrCreateBySocialId("kakao", "12345", "user@example.com"))
                .thenReturn(new UserService.FindOrCreateResult(user, false));
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

        AuthService.LoginResult result = authService.login("kakao", "kakao-token", "device");

        assertFalse(result.isNewUser());
    }

    @Test
    @DisplayName("login: 로그인할 때마다 서로 다른 refresh token이 발급된다")
    void loginTokensAreUnique() {
        when(kakaoOAuthService.fetchProfile("kakao-token"))
                .thenReturn(new KakaoOAuthService.KakaoProfile("12345", null));
        when(userService.findOrCreateBySocialId("kakao", "12345", null))
                .thenReturn(new UserService.FindOrCreateResult(user, false));
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

        String first = authService.login("kakao", "kakao-token", "device").refreshToken();
        String second = authService.login("kakao", "kakao-token", "device").refreshToken();

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("login: 카카오 토큰 검증에 실패하면 예외가 전파되고 아무것도 저장하지 않는다")
    void loginInvalidSocialToken() {
        when(kakaoOAuthService.fetchProfile("bad-token")).thenThrow(new InvalidSocialTokenException());

        assertThrows(InvalidSocialTokenException.class,
                () -> authService.login("kakao", "bad-token", "device"));

        verifyNoInteractions(userService, refreshTokenRepository);
    }

    // -------------------------------------------------------------- refresh

    @Test
    @DisplayName("refresh: 유효한 토큰이면 기존 토큰을 폐기하고 새 토큰을 발급한다")
    void refreshSuccess() throws Exception {
        String raw = "raw-refresh-token";
        RefreshToken existing = storedToken(raw, LocalDateTime.now().plusDays(10));
        when(refreshTokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(existing));
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

        AuthService.LoginResult result = authService.refresh(raw, "new-device");

        assertEquals("new-access-token", result.accessToken());
        assertEquals(3600L, result.expiresIn());
        assertFalse(result.isNewUser());
        assertSame(user, result.user());
        assertNotEquals(raw, result.refreshToken());

        // 기존 토큰은 폐기되고 사용 시각이 기록된다 (rotation).
        assertNotNull(existing.getRevokedAt());
        assertNotNull(existing.getLastUsedAt());
        assertFalse(existing.isValid());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken rotated = captor.getValue();
        assertSame(user, rotated.getUser());
        assertEquals("new-device", rotated.getDeviceInfo());
        assertEquals(sha256(result.refreshToken()), rotated.getTokenHash());
        assertNull(rotated.getRevokedAt());
        assertTrue(rotated.isValid());
    }

    @Test
    @DisplayName("refresh: 존재하지 않는 토큰이면 InvalidRefreshTokenException")
    void refreshUnknownToken() throws Exception {
        when(refreshTokenRepository.findByTokenHash(sha256("unknown"))).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh("unknown", "device"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("refresh: 이미 폐기된 토큰은 재사용할 수 없다")
    void refreshRevokedToken() throws Exception {
        String raw = "revoked-token";
        RefreshToken existing = storedToken(raw, LocalDateTime.now().plusDays(10));
        existing.revoke();
        when(refreshTokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(existing));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(raw, "device"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("refresh: 만료된 토큰은 거부한다")
    void refreshExpiredToken() throws Exception {
        String raw = "expired-token";
        RefreshToken existing = storedToken(raw, LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(existing));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(raw, "device"));

        verify(refreshTokenRepository, never()).save(any());
    }

    // --------------------------------------------------------------- logout

    @Test
    @DisplayName("logout: 토큰을 폐기한다")
    void logoutRevokes() throws Exception {
        String raw = "logout-token";
        RefreshToken existing = storedToken(raw, LocalDateTime.now().plusDays(10));
        when(refreshTokenRepository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(existing));

        authService.logout(raw);

        assertNotNull(existing.getRevokedAt());
        assertFalse(existing.isValid());
    }

    @Test
    @DisplayName("logout: 존재하지 않는 토큰이어도 예외 없이 끝난다")
    void logoutUnknownToken() throws Exception {
        when(refreshTokenRepository.findByTokenHash(sha256("unknown"))).thenReturn(Optional.empty());

        authService.logout("unknown");

        verify(refreshTokenRepository).findByTokenHash(sha256("unknown"));
    }
}
