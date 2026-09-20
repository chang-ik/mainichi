package com.mainichi.api.global.exception;


import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** API 문서의 공통 에러 포맷({ "error": { "code", "message" } })에 맞춰 예외를 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidSocialTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSocialToken() {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_SOCIAL_TOKEN", "소셜 토큰 검증에 실패했습니다.");
    }

    @ExceptionHandler(UnsupportedProviderException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedProvider() {
        return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER", "지원하지 않는 provider 값입니다.");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefreshToken() {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "토큰이 만료되었거나 이미 폐기되었습니다.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
