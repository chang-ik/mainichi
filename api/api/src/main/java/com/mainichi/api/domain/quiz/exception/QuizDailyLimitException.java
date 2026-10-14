package com.mainichi.api.domain.quiz.exception;

/** 하루에 만들 수 있는 문제 수를 넘은 경우 */
public class QuizDailyLimitException extends RuntimeException {
    public QuizDailyLimitException() {
        super("QUIZ_DAILY_LIMIT");
    }
}
