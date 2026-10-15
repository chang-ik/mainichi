package com.mainichi.api.global.exception;


import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import com.mainichi.api.domain.board.exception.BoardLevelNotFoundException;
import com.mainichi.api.domain.board.exception.CommentNotFoundException;
import com.mainichi.api.domain.board.exception.NotAuthorException;
import com.mainichi.api.domain.board.exception.PostNotFoundException;
import com.mainichi.api.domain.quiz.exception.NotEnoughNewCardsException;
import com.mainichi.api.domain.quiz.exception.QuizBusyException;
import com.mainichi.api.domain.quiz.exception.QuizDailyLimitException;
import com.mainichi.api.domain.quiz.exception.QuizGenerationException;
import com.mainichi.api.domain.quiz.exception.QuizKindNotFoundException;
import com.mainichi.api.domain.quiz.exception.QuizNotFoundException;
import com.mainichi.api.domain.quiz.exception.QuizUnavailableException;
import com.mainichi.api.domain.stats.exception.InvalidStatsQueryException;
import com.mainichi.api.domain.study.exception.CardNotFoundException;
import com.mainichi.api.domain.study.exception.StudyKindNotFoundException;
import com.mainichi.api.domain.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(NotEnoughNewCardsException.class)
    public ResponseEntity<Map<String, Object>> handleNotEnoughNewCards() {
        return error(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_NEW_CARDS", "오늘 새로 배운 카드가 부족해요. 단어·문법을 조금 더 공부한 뒤 다시 시도해 주세요.");
    }

    @ExceptionHandler(QuizDailyLimitException.class)
    public ResponseEntity<Map<String, Object>> handleQuizDailyLimit() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "QUIZ_DAILY_LIMIT", "오늘 만들 수 있는 문제를 모두 만들었어요. 내일 다시 도전해 주세요.");
    }

    @ExceptionHandler(QuizNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleQuizNotFound() {
        return error(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "문제를 찾을 수 없습니다.");
    }

    @ExceptionHandler(QuizKindNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleQuizKindNotFound() {
        return error(HttpStatus.NOT_FOUND, "QUIZ_KIND_NOT_FOUND", "지원하지 않는 문제 종류입니다.");
    }

    @ExceptionHandler(QuizGenerationException.class)
    public ResponseEntity<Map<String, Object>> handleQuizGeneration() {
        return error(HttpStatus.BAD_GATEWAY, "QUIZ_GENERATION_FAILED", "문제를 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(QuizBusyException.class)
    public ResponseEntity<Map<String, Object>> handleQuizBusy() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "QUIZ_BUSY", "AI 사용량이 잠시 꽉 찼어요. 1분쯤 뒤에 다시 시도해 주세요.");
    }

    @ExceptionHandler(QuizUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleQuizUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_UNAVAILABLE", "지금은 문제를 만들 수 없어요. (서버에 AI API 키가 설정되지 않음)");
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePostNotFound() {
        return error(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "글을 찾을 수 없습니다. 삭제되었을 수 있어요.");
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCommentNotFound() {
        return error(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
    }

    @ExceptionHandler(BoardLevelNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBoardLevelNotFound() {
        return error(HttpStatus.NOT_FOUND, "BOARD_LEVEL_NOT_FOUND", "없는 단계의 게시판입니다.");
    }

    @ExceptionHandler(NotAuthorException.class)
    public ResponseEntity<Map<String, Object>> handleNotAuthor() {
        return error(HttpStatus.FORBIDDEN, "NOT_AUTHOR", "내가 쓴 글·댓글만 고치거나 지울 수 있어요.");
    }

    // 여기서 처리하지 않으면 /error 로 넘어가 공통 포맷이 아닌 응답이 나간다.
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, InvalidStatsQueryException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
