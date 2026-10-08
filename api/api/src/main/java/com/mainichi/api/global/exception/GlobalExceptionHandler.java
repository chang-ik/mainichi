package com.mainichi.api.global.exception;


import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import com.mainichi.api.domain.study.exception.CardNotFoundException;
import com.mainichi.api.domain.study.exception.StudyKindNotFoundException;
import com.mainichi.api.domain.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /** 탈퇴 후에도 아직 만료되지 않은 액세스 토큰으로 요청한 경우 등. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound() {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
    }

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCardNotFound() {
        return error(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "카드를 찾을 수 없습니다.");
    }

    @ExceptionHandler(StudyKindNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStudyKindNotFound() {
        return error(HttpStatus.NOT_FOUND, "STUDY_KIND_NOT_FOUND", "지원하지 않는 학습 종류입니다.");
    }

    // 여기서 처리하지 않으면 /error 로 넘어가 공통 포맷이 아닌 응답이 나간다.
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
