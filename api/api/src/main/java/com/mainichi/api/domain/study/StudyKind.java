package com.mainichi.api.domain.study;

import com.mainichi.api.domain.card.entity.Card;

import java.util.Arrays;
import java.util.Optional;

/** 카드로 공부하는 학습 종류. URL(/study/{path})과 card.card_tag 를 잇는다. */
public enum StudyKind {
    WORD("words", Card.TAG_WORD),
    GRAMMAR("grammar", Card.TAG_GRAMMAR);

    private final String path;
    private final String cardTag;

    StudyKind(String path, String cardTag) {
        this.path = path;
        this.cardTag = cardTag;
    }

    public String cardTag() {
        return cardTag;
    }

    public static Optional<StudyKind> fromPath(String path) {
        return Arrays.stream(values()).filter(k -> k.path.equals(path)).findFirst();
    }
}
