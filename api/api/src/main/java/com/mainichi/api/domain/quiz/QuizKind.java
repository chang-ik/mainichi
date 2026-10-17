package com.mainichi.api.domain.quiz;

import java.util.Arrays;
import java.util.Optional;

/** Claude 가 만드는 문제 종류. URL(/quiz/{path})과 quiz.kind 를 잇는다. */
public enum QuizKind {
    /** 장문(독해): 오늘 배운 카드로 쓴 지문 + 문제 2개. 카드가 3장은 있어야 만든다. */
    READING("reading", 3),
    /** 청해: 단계에 맞는 대화 + 문제 2개. 오늘 배운 카드가 있으면 섞어 쓰고, 없어도 만든다. */
    LISTENING("listening", 0);

    private final String path;
    private final int minNewCards;

    QuizKind(String path, int minNewCards) {
        this.path = path;
        this.minNewCards = minNewCards;
    }

    public int minNewCards() {
        return minNewCards;
    }

    public static Optional<QuizKind> fromPath(String path) {
        return Arrays.stream(values()).filter(k -> k.path.equals(path)).findFirst();
    }
}
