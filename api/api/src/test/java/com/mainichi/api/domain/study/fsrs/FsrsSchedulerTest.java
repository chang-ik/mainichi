package com.mainichi.api.domain.study.fsrs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 기대값은 FSRS-5 식(py-fsrs 5.x)과 기본 파라미터로 직접 계산한 값이다. */
class FsrsSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 9, 0);
    private final FsrsScheduler scheduler = new FsrsScheduler(FsrsSettings.defaults());

    private ReviewOutcome reviewNew(Rating rating) {
        return scheduler.review(FsrsCard.newCard(NOW), rating, NOW);
    }

    @Nested
    @DisplayName("새 카드")
    class NewCard {

        @Test
        @DisplayName("✕ 다시: 학습 첫 단계(1분)로, 초기 안정도 w0·난이도 w4")
        void again() {
            ReviewOutcome o = reviewNew(Rating.AGAIN);

            assertThat(o.card().phase()).isEqualTo(CardPhase.LEARNING);
            assertThat(o.card().step()).isZero();
            assertThat(o.interval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(o.card().stability()).isCloseTo(0.40255, within(1e-9));
            assertThat(o.card().difficulty()).isCloseTo(7.1949, within(1e-9));
        }

        @Test
        @DisplayName("△ 어려움: 첫 두 단계의 평균(5분 30초)")
        void hard() {
            ReviewOutcome o = reviewNew(Rating.HARD);

            assertThat(o.card().phase()).isEqualTo(CardPhase.LEARNING);
            assertThat(o.card().step()).isZero();
            assertThat(o.interval()).isEqualTo(Duration.ofSeconds(330));
            assertThat(o.card().difficulty()).isCloseTo(6.488305, within(1e-6));
        }

        @Test
        @DisplayName("○ 알맞음: 다음 단계(10분)로")
        void good() {
            ReviewOutcome o = reviewNew(Rating.GOOD);

            assertThat(o.card().phase()).isEqualTo(CardPhase.LEARNING);
            assertThat(o.card().step()).isEqualTo(1);
            assertThat(o.interval()).isEqualTo(Duration.ofMinutes(10));
            assertThat(o.card().due()).isEqualTo(NOW.plusMinutes(10));
            assertThat(o.card().stability()).isCloseTo(3.173, within(1e-9));
            assertThat(o.card().difficulty()).isCloseTo(5.282434, within(1e-6));
        }

        @Test
        @DisplayName("◎ 쉬움: 바로 복습 단계로 졸업, 간격 16일")
        void easy() {
            ReviewOutcome o = reviewNew(Rating.EASY);

            assertThat(o.card().phase()).isEqualTo(CardPhase.REVIEW);
            assertThat(o.card().step()).isNull();
            assertThat(o.interval()).isEqualTo(Duration.ofDays(16));
            assertThat(o.scheduledDays()).isEqualTo(16);
        }
    }

    @Test
    @DisplayName("학습 마지막 단계에서 ○ 이면 단기 안정도 식으로 졸업한다 (4일)")
    void graduateFromLearning() {
        FsrsCard afterFirstGood = reviewNew(Rating.GOOD).card();

        ReviewOutcome o = scheduler.review(afterFirstGood, Rating.GOOD, NOW.plusMinutes(10));

        assertThat(o.card().phase()).isEqualTo(CardPhase.REVIEW);
        assertThat(o.card().stability()).isCloseTo(4.466858, within(1e-6));
        assertThat(o.card().difficulty()).isCloseTo(5.272968, within(1e-6));
        assertThat(o.interval()).isEqualTo(Duration.ofDays(4));
        assertThat(o.elapsedDays()).isZero();
    }

    @Test
    @DisplayName("학습 중 ✕ 이면 첫 단계로 돌아간다")
    void againInLearningResetsStep() {
        FsrsCard step1 = reviewNew(Rating.GOOD).card();

        ReviewOutcome o = scheduler.review(step1, Rating.AGAIN, NOW.plusMinutes(10));

        assertThat(o.card().phase()).isEqualTo(CardPhase.LEARNING);
        assertThat(o.card().step()).isZero();
        assertThat(o.interval()).isEqualTo(Duration.ofMinutes(1));
    }

    @Nested
    @DisplayName("복습 카드 (안정도 10, 난이도 5, 10일 만에 복습 → 기억률 0.9)")
    class ReviewCard {

        private final LocalDateTime lastReview = NOW.minusDays(10);
        private final FsrsCard card = new FsrsCard(CardPhase.REVIEW, null, 10.0, 5.0, NOW, lastReview);

        @Test
        @DisplayName("기억률은 안정도만큼 지났을 때 정확히 목표치(0.9)")
        void retrievability() {
            assertThat(scheduler.retrievability(card, NOW)).isCloseTo(0.9, within(1e-12));
        }

        @Test
        @DisplayName("△ ○ ◎ 순서로 간격이 15일 · 33일 · 79일")
        void intervals() {
            assertThat(scheduler.review(card, Rating.HARD, NOW).interval()).isEqualTo(Duration.ofDays(15));
            assertThat(scheduler.review(card, Rating.GOOD, NOW).interval()).isEqualTo(Duration.ofDays(33));
            assertThat(scheduler.review(card, Rating.EASY, NOW).interval()).isEqualTo(Duration.ofDays(79));

            ReviewOutcome good = scheduler.review(card, Rating.GOOD, NOW);
            assertThat(good.card().stability()).isCloseTo(32.954264, within(1e-6));
            assertThat(good.elapsedDays()).isEqualTo(10);
        }

        @Test
        @DisplayName("✕ 이면 재학습(10분)으로 가고 안정도가 망각 식으로 줄어든다")
        void lapse() {
            ReviewOutcome o = scheduler.review(card, Rating.AGAIN, NOW);

            assertThat(o.card().phase()).isEqualTo(CardPhase.RELEARNING);
            assertThat(o.card().step()).isZero();
            assertThat(o.interval()).isEqualTo(Duration.ofMinutes(10));
            assertThat(o.card().stability()).isCloseTo(2.107696, within(1e-6));
        }

        @Test
        @DisplayName("재학습에서 ○ 이면 다시 복습 단계로 졸업한다 (최소 1일)")
        void relearnGraduates() {
            FsrsCard relearning = scheduler.review(card, Rating.AGAIN, NOW).card();

            ReviewOutcome o = scheduler.review(relearning, Rating.GOOD, NOW.plusMinutes(10));

            assertThat(o.card().phase()).isEqualTo(CardPhase.REVIEW);
            assertThat(o.interval()).isGreaterThanOrEqualTo(Duration.ofDays(1));
        }
    }

    @Test
    @DisplayName("최대 간격을 넘지 않는다")
    void maximumInterval() {
        FsrsSettings d = FsrsSettings.defaults();
        FsrsScheduler capped = new FsrsScheduler(
                new FsrsSettings(d.weights(), d.requestRetention(), 30, d.learningSteps(), d.relearningSteps()));
        FsrsCard strong = new FsrsCard(CardPhase.REVIEW, null, 300.0, 3.0, NOW, NOW.minusDays(300));

        assertThat(capped.review(strong, Rating.EASY, NOW).interval()).isEqualTo(Duration.ofDays(30));
    }
}
