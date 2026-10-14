package com.mainichi.api.domain.quiz.exception;

/** Claude API 키가 설정되지 않아 문제를 만들 수 없는 경우 */
public class QuizUnavailableException extends RuntimeException {
    public QuizUnavailableException() {
        super("QUIZ_UNAVAILABLE");
    }
}
