package com.mainichi.api.domain.quiz.exception;

/** 없거나 다른 유저의 문제 */
public class QuizNotFoundException extends RuntimeException {
    public QuizNotFoundException() {
        super("QUIZ_NOT_FOUND");
    }
}
