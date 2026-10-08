package com.mainichi.api.domain.study.fsrs;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FSRS-5 스케줄러. py-fsrs 5.x 의 Scheduler 와 같은 식을 쓴다 (fuzz 는 적용하지 않음).
 *
 * 흐름
 *   NEW ──복습──▶ LEARNING (학습 단계 1분 → 10분) ──졸업──▶ REVIEW (일 단위 간격)
 *   REVIEW ──✕──▶ RELEARNING (재학습 단계 10분) ──졸업──▶ REVIEW
 *
 * DB 나 스프링에 의존하지 않는 순수 계산 클래스라 단위 테스트로 검증한다.
 */
public class FsrsScheduler {

    private static final double DECAY = -0.5;
    private static final double FACTOR = 19.0 / 81.0; // 0.9^(1/DECAY) - 1
    private static final double STABILITY_MIN = 0.01;

    private final FsrsSettings settings;
    private final double[] w;

    public FsrsScheduler(FsrsSettings settings) {
        this.settings = settings;
        this.w = settings.weights();
    }

    /** 지금 시점에 이 카드를 기억하고 있을 확률 (0~1). NEW 면 0. */
    public double retrievability(FsrsCard card, LocalDateTime now) {
        if (card.lastReview() == null || card.stability() == null) {
            return 0;
        }
        long elapsedDays = Math.max(0, Duration.between(card.lastReview(), now).toDays());
        return Math.pow(1 + FACTOR * elapsedDays / card.stability(), DECAY);
    }

    public ReviewOutcome review(FsrsCard card, Rating rating, LocalDateTime now) {
        Integer daysSinceLastReview = card.lastReview() == null
                ? null
                : (int) Math.max(0, Duration.between(card.lastReview(), now).toDays());

        // 1) 안정도·난이도 갱신
        double stability;
        double difficulty;
        if (card.stability() == null || card.difficulty() == null) {
            stability = initialStability(rating);
            difficulty = initialDifficulty(rating);
        } else if (daysSinceLastReview != null && daysSinceLastReview < 1) {
            // 같은 날 다시 본 경우(학습 단계 등)는 단기 기억 식을 쓴다.
            stability = shortTermStability(card.stability(), rating);
            difficulty = nextDifficulty(card.difficulty(), rating);
        } else {
            double r = retrievability(card, now);
            stability = nextStability(card.difficulty(), card.stability(), r, rating);
            difficulty = nextDifficulty(card.difficulty(), rating);
        }
        stability = Math.max(stability, STABILITY_MIN);

        // 2) 단계 전이와 다음 간격
        CardPhase phase = card.phase();
        Integer step = card.step();
        Duration interval;

        switch (phase) {
            case NEW, LEARNING -> {
                Transition t = stepTransition(settings.learningSteps(), stepOrZero(step), rating, stability);
                phase = t.graduated ? CardPhase.REVIEW : CardPhase.LEARNING;
                step = t.step;
                interval = t.interval;
            }
            case REVIEW -> {
                if (rating == Rating.AGAIN && !settings.relearningSteps().isEmpty()) {
                    phase = CardPhase.RELEARNING;
                    step = 0;
                    interval = settings.relearningSteps().get(0);
                } else {
                    step = null;
                    interval = Duration.ofDays(nextIntervalDays(stability));
                }
            }
            case RELEARNING -> {
                Transition t = stepTransition(settings.relearningSteps(), stepOrZero(step), rating, stability);
                phase = t.graduated ? CardPhase.REVIEW : CardPhase.RELEARNING;
                step = t.step;
                interval = t.interval;
            }
            default -> throw new IllegalStateException("unknown phase: " + phase);
        }

        FsrsCard next = new FsrsCard(phase, step, stability, difficulty, now.plus(interval), now);
        int scheduledDays = (int) interval.toDays();
        return new ReviewOutcome(next, interval, daysSinceLastReview == null ? 0 : daysSinceLastReview, scheduledDays);
    }

    // ---------------------------------------------------------------- 단계 전이

    private record Transition(boolean graduated, Integer step, Duration interval) {}

    /** 학습/재학습 단계 공통 규칙 (py-fsrs 5 와 동일). */
    private Transition stepTransition(List<Duration> steps, int step, Rating rating, double stability) {
        Transition graduate = new Transition(true, null, Duration.ofDays(nextIntervalDays(stability)));

        // 단계가 없거나, 설정이 바뀌어 현재 단계가 범위를 벗어난 경우
        if (steps.isEmpty() || (step >= steps.size() && rating != Rating.AGAIN)) {
            return graduate;
        }

        return switch (rating) {
            case AGAIN -> new Transition(false, 0, steps.get(0));
            case HARD -> {
                Duration interval;
                if (step == 0 && steps.size() == 1) {
                    interval = multiply(steps.get(0), 1.5);
                } else if (step == 0) {
                    interval = steps.get(0).plus(steps.get(1)).dividedBy(2);
                } else {
                    interval = steps.get(step);
                }
                yield new Transition(false, step, interval);
            }
            case GOOD -> step + 1 >= steps.size()
                    ? graduate
                    : new Transition(false, step + 1, steps.get(step + 1));
            case EASY -> graduate;
        };
    }

    private static int stepOrZero(Integer step) {
        return step == null ? 0 : step;
    }

    private static Duration multiply(Duration d, double factor) {
        return Duration.ofMillis(Math.round(d.toMillis() * factor));
    }

    // ---------------------------------------------------------------- FSRS-5 식

    private double initialStability(Rating rating) {
        return Math.max(w[rating.value() - 1], STABILITY_MIN);
    }

    private double initialDifficulty(Rating rating) {
        return clampDifficulty(rawInitialDifficulty(rating));
    }

    private double rawInitialDifficulty(Rating rating) {
        return w[4] - Math.exp(w[5] * (rating.value() - 1)) + 1;
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double delta = -w[6] * (rating.value() - 3);
        double damped = difficulty + delta * (10 - difficulty) / 9; // 10 에 가까울수록 덜 오른다
        double reverted = w[7] * rawInitialDifficulty(Rating.EASY) + (1 - w[7]) * damped;
        return clampDifficulty(reverted);
    }

    private double shortTermStability(double stability, Rating rating) {
        return stability * Math.exp(w[17] * (rating.value() - 3 + w[18]));
    }

    private double nextStability(double difficulty, double stability, double retrievability, Rating rating) {
        return rating == Rating.AGAIN
                ? nextForgetStability(difficulty, stability, retrievability)
                : nextRecallStability(difficulty, stability, retrievability, rating);
    }

    private double nextRecallStability(double d, double s, double r, Rating rating) {
        double hardPenalty = rating == Rating.HARD ? w[15] : 1;
        double easyBonus = rating == Rating.EASY ? w[16] : 1;
        return s * (1 + Math.exp(w[8]) * (11 - d) * Math.pow(s, -w[9])
                * (Math.exp(w[10] * (1 - r)) - 1) * hardPenalty * easyBonus);
    }

    private double nextForgetStability(double d, double s, double r) {
        double longTerm = w[11] * Math.pow(d, -w[12]) * (Math.pow(s + 1, w[13]) - 1) * Math.exp(w[14] * (1 - r));
        double shortTerm = s / Math.exp(w[17] * w[18]);
        return Math.min(longTerm, shortTerm);
    }

    int nextIntervalDays(double stability) {
        double interval = stability / FACTOR * (Math.pow(settings.requestRetention(), 1 / DECAY) - 1);
        long rounded = Math.round(interval);
        return (int) Math.min(Math.max(rounded, 1), settings.maximumInterval());
    }

    private static double clampDifficulty(double d) {
        return Math.min(Math.max(d, 1), 10);
    }
}
