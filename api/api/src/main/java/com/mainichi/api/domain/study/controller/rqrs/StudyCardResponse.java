package com.mainichi.api.domain.study.controller.rqrs;

/**
 * 학습 화면에 보여줄 카드 (단어·문법).
 * front / example 은 후리가나 대괄호 표기 그대로 내려주고, 화면에서 ruby 로 바꾼다.
 *
 * @param phase     NEW / LEARNING / REVIEW / RELEARNING
 * @param usage     문법 카드의 접속 형태. 단어 카드는 null
 * @param intervals 각 버튼을 눌렀을 때 다음 복습까지 간격(초). 버튼 아래 "10분", "3일" 같은 안내에 쓴다.
 */
public record StudyCardResponse(
        String id,
        String phase,
        String front,
        String meaning,
        String example,
        String exampleMeaning,
        String usage,
        Intervals intervals
) {
    public record Intervals(long again, long hard, long good, long easy) {}
}
