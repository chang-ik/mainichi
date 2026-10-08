package com.mainichi.api.domain.study.exception;

public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException() {
        super("CARD_NOT_FOUND");
    }
}
