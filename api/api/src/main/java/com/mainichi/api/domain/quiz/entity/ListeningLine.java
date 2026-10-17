package com.mainichi.api.domain.quiz.entity;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 청해 대화 한 줄 (quiz.script 에 JSON 으로 저장, structured output 스키마로도 쓴다).
 * 웹은 이 text 의 읽는 법(후리가나)을 브라우저 음성 합성으로 읽어 준다.
 */
public record ListeningLine(
        @JsonPropertyDescription("말하는 사람. MALE=남자, FEMALE=여자")
        Speaker speaker,
        @JsonPropertyDescription("대사. 일본어, 후리가나 대괄호 표기")
        String text
) {
    public enum Speaker { MALE, FEMALE }
}
