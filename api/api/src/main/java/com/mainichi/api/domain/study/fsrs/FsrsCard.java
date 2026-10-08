package com.mainichi.api.domain.study.fsrs;

import java.time.LocalDateTime;

/**
 * 스케줄러가 다루는 카드의 FSRS 상태 (불변).
 *
 * @param step       학습/재학습 단계 인덱스. REVIEW 면 null
 * @param stability  기억 안정도(일). NEW 면 null
 * @param difficulty 난이도 1~10. NEW 면 null
 * @param lastReview 마지막 복습 시각. NEW 면 null
 */
public record FsrsCard(
        CardPhase phase,
        Integer step,
        Double stability,
        Double difficulty,
        LocalDateTime due,
        LocalDateTime lastReview
) {
    public static FsrsCard newCard(LocalDateTime now) {
        return new FsrsCard(CardPhase.NEW, 0, null, null, now, null);
    }
}
