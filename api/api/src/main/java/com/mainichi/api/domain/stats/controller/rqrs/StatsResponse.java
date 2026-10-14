package com.mainichi.api.domain.stats.controller.rqrs;

import java.time.LocalDate;
import java.util.List;

/**
 * Anki 통계 화면을 본뜬 학습 통계. 날짜는 모두 유저 시간대 기준이다.
 * young/mature 는 Anki 처럼 복습 간격 21일 미만/이상으로 나눈다.
 *
 * @param deck  all / words / grammar
 * @param days  기간(일). 복습 그래프·버튼·시간대 통계와 미래 예정 범위
 * @param today 오늘 날짜
 */
public record StatsResponse(
        String deck,
        int days,
        LocalDate today,
        Today todayStats,
        Streak streak,
        List<DayCount> calendar,
        List<DayReviews> reviews,
        ReviewSummary reviewSummary,
        Forecast forecast,
        CardCounts cardCounts,
        Histogram intervals,
        Histogram stability,
        Histogram difficulty,
        Histogram retrievability,
        List<HourStat> hours,
        List<ButtonStat> buttons,
        List<RetentionRow> retention
) {

    /** 오늘: 복습 수, 시간, 종류별 수, 다시(✕) 수, 정답률(✕가 아닌 비율) */
    public record Today(long reviews, long seconds, long newCards, long learning, long review, long relearning,
                        long again, Double correctRate, long matureReviews, Double matureCorrectRate) {}

    /** 연속 학습일. 오늘 아직 안 했으면 어제까지 이어진 날 수 */
    public record Streak(int current, int longest, int studiedDays, int periodDays) {}

    /** 달력(히트맵): 최근 1년 중 복습한 날만 */
    public record DayCount(LocalDate date, long count) {}

    /** 하루 복습 수 (복습 전 단계 기준) */
    public record DayReviews(LocalDate date, long learning, long review, long relearning, long newCards, long seconds) {}

    public record ReviewSummary(long total, int daysStudied, double averagePerStudiedDay, double averagePerDay,
                                long seconds) {}

    /**
     * 미래 예정 복습.
     *
     * @param due       오늘부터 하루 단위 예정 카드 수 (0 = 오늘, 밀린 카드 포함)
     * @param overdue   기한이 지난 카드 수
     * @param dailyLoad 하루 평균 예상 복습량 (Anki: 각 카드 1/간격 의 합)
     */
    public record Forecast(List<Long> due, long dueTomorrow, long overdue, long dailyLoad) {}

    /** 카드 수. newCards 는 지금 학습 단계 덱에서 아직 안 본 카드 */
    public record CardCounts(long newCards, long learning, long relearning, long young, long mature) {}

    public record Bucket(String label, long count) {}

    /** average 는 각 그래프 단위(일 또는 %), 카드가 없으면 null */
    public record Histogram(List<Bucket> buckets, Double average, Double max) {}

    public record HourStat(int hour, long count, Double correctRate) {}

    /** 평가 버튼별 횟수 (counts[0]=다시 … counts[3]=쉬움). group: LEARNING / YOUNG / MATURE */
    public record ButtonStat(String group, List<Long> counts, Double correctRate) {}

    /** Anki "실제 기억률": 복습 단계 카드의 통과(✕ 아님)/실패 */
    public record RetentionRow(String label, long youngPass, long youngFail, long maturePass, long matureFail) {}
}
