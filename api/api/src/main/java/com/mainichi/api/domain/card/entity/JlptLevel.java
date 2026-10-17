package com.mainichi.api.domain.card.entity;

/**
 * user.current_level / card.level 값의 의미.
 * 1~5 는 JLPT N1~N5, 0 은 기초(가나).
 */
public final class JlptLevel {

    public static final int BASIC = 0;
    public static final int N5 = 5;

    private JlptLevel() {}

    public static boolean isValid(int level) {
        return level >= BASIC && level <= N5;
    }

    /**
     * 학습에 쓸 단계. 기초(0)는 가나·기초 문법 덱, 1~5 는 해당 JLPT 덱. 범위를 벗어난 값은 N5 로 본다.
     */
    public static int forStudy(Integer currentLevel) {
        return currentLevel != null && isValid(currentLevel) ? currentLevel : N5;
    }
}
