package com.mainichi.api.domain.quiz.exception;

/** AI 서비스의 사용량 한도(무료 등급의 분당·하루 요청 수 등)에 걸린 경우 */
public class QuizBusyException extends RuntimeException {
    public QuizBusyException() {
        super("QUIZ_BUSY");
    }
}
