package com.mainichi.api.domain.quiz.exception;

/** 오늘 새로 배운 카드가 너무 적어 지문을 만들 수 없는 경우 */
public class NotEnoughNewCardsException extends RuntimeException {
    public NotEnoughNewCardsException() {
        super("NOT_ENOUGH_NEW_CARDS");
    }
}
