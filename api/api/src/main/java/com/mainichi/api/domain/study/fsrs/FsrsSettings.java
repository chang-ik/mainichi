package com.mainichi.api.domain.study.fsrs;

import java.time.Duration;
import java.util.List;

/**
 * 스케줄러 설정. user_fsrs_param 한 행에서 만들어진다.
 *
 * @param weights          FSRS-5 파라미터 19개
 * @param requestRetention 목표 기억률 (0.9 = 복습 시점에 90% 기억하도록 간격을 잡는다)
 * @param maximumInterval  최대 복습 간격(일)
 */
public record FsrsSettings(
        double[] weights,
        double requestRetention,
        int maximumInterval,
        List<Duration> learningSteps,
        List<Duration> relearningSteps
) {
    /** py-fsrs 5.x 기본값 */
    public static final double[] DEFAULT_WEIGHTS = {
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    };

    public FsrsSettings {
        if (weights.length != 19) {
            throw new IllegalArgumentException("FSRS-5 weights must have 19 values: " + weights.length);
        }
        weights = weights.clone();
        learningSteps = List.copyOf(learningSteps);
        relearningSteps = List.copyOf(relearningSteps);
    }

    public static FsrsSettings defaults() {
        return new FsrsSettings(
                DEFAULT_WEIGHTS, 0.9, 36500,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(10)),
                List.of(Duration.ofMinutes(10))
        );
    }
}
