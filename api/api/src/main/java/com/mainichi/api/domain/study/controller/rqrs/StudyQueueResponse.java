package com.mainichi.api.domain.study.controller.rqrs;

/**
 * 지금 공부할 카드 한 장과 남은 개수.
 *
 * @param card             지금 보여줄 카드. 오늘 할 게 없으면 null
 * @param nextDueInSeconds card 가 null 인데 오늘 안에 다시 볼 학습 중 카드가 있으면, 그때까지 남은 초. 없으면 null
 */
public record StudyQueueResponse(Counts counts, StudyCardResponse card, Long nextDueInSeconds) {

    /** 오늘 남은 카드 수 (새 카드 / 학습 중 / 복습) */
    public record Counts(long newCards, long learning, long review) {
        public long total() {
            return newCards + learning + review;
        }
    }
}
