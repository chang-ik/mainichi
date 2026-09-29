package com.mainichi.api.domain.auth.controller;

import com.mainichi.api.domain.auth.controller.rqrs.LoginRequest;
import com.mainichi.api.domain.auth.controller.rqrs.LoginResponse;
import com.mainichi.api.domain.auth.controller.rqrs.RefreshResponse;
import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest
    ){
        AuthService.LoginResult result = authService.login(request.provider(), request.code(), deviceInfo(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(toLoginResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest
    ){
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        AuthService.LoginResult result = authService.refresh(refreshToken, deviceInfo(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(new RefreshResponse(result.accessToken(), "Bearer", result.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken
    ){
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        // 쿠키가 없어도 브라우저에 남은 쿠키는 확실히 지운다.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.expire().toString())
                .build();
    }

    private LoginResponse toLoginResponse(AuthService.LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                "Bearer",
                result.expiresIn(),
                result.isNewUser(),
                new LoginResponse.UserSummary(
                        result.user().getId().toString(),
                        result.user().getNickname(),
                        result.user().getEmail(),
                        result.user().getProvider(),
                        result.user().getCurrentLevel(),
                        result.user().getTimezone()
                )
        );
    }

    private String deviceInfo(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
