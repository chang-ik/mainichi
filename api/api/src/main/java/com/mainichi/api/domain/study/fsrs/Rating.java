package com.mainichi.api.domain.study.fsrs;

import java.util.Arrays;

/** 복습 평가. 화면의 ✕ △ ○ ◎ 버튼과 1:1 대응. */
public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    private final int value;

    Rating(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Rating fromValue(int value) {
        return Arrays.stream(values())
                .filter(r -> r.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("rating must be 1~4: " + value));
    }
}
