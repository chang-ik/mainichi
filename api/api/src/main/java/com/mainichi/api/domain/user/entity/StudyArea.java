package com.mainichi.api.domain.user.entity;

/**
 * 학습 영역. 단어(WORD)는 항상 포함되고, 나머지는 유저가 "학습 변경"에서 고른다.
 */
public enum StudyArea {
    WORD,
    GRAMMAR,
    READING,
    LISTENING
}
