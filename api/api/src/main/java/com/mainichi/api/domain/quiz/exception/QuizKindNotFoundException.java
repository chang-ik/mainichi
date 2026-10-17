package com.mainichi.api.domain.quiz.exception;

/** /quiz/{kind} 에 없는 문제 종류가 들어온 경우 */
public class QuizKindNotFoundException extends RuntimeException {
    public QuizKindNotFoundException() {
        super("QUIZ_KIND_NOT_FOUND");
    }
}
