package com.mainichi.api.domain.auth.controller;

import com.mainichi.api.domain.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 리프레시 토큰을 httpOnly 쿠키로 주고받는다.
 * JS 에서 읽을 수 없어 XSS 로 토큰 값이 유출되지 않고, Path 를 /auth 로 제한해 다른 API 요청에는 실리지 않는다.
 */
@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/auth";

    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieProvider(
            @Value("${auth.refresh-cookie.secure:true}") boolean secure,
            @Value("${auth.refresh-cookie.same-site:Strict}") String sameSite
    ){
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie create(String refreshToken){
        return build(refreshToken, Duration.ofDays(AuthService.REFRESH_TOKEN_VALIDITY_DAYS));
    }

    /** 같은 이름/경로의 쿠키를 maxAge 0 으로 내려 브라우저에서 지운다. */
    public ResponseCookie expire(){
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge){
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
