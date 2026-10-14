package com.mainichi.api.domain.quiz.exception;

/** Claude 가 문제를 만들지 못했거나 응답 형식이 맞지 않는 경우 */
public class QuizGenerationException extends RuntimeException {
    public QuizGenerationException() {
        super("QUIZ_GENERATION_FAILED");
    }
}
