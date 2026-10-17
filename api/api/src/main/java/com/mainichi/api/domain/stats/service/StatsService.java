package com.mainichi.api.domain.stats.service;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.card.entity.JlptLevel;
import com.mainichi.api.domain.card.repository.CardRepository;
import com.mainichi.api.domain.stats.controller.rqrs.StatsResponse;
import com.mainichi.api.domain.stats.controller.rqrs.StatsResponse.*;
import com.mainichi.api.domain.stats.exception.InvalidStatsQueryException;
import com.mainichi.api.domain.study.StudyDay;
import com.mainichi.api.domain.study.StudyKind;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import com.mainichi.api.domain.study.fsrs.FsrsCard;
import com.mainichi.api.domain.study.fsrs.FsrsScheduler;
import com.mainichi.api.domain.study.fsrs.FsrsSettings;
import com.mainichi.api.domain.study.repository.CardStateRepository;
import com.mainichi.api.domain.study.repository.ReviewLogRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Anki 통계 화면을 본뜬 학습 통계. 최근 1년 복습 기록과 현재 카드 상태를 한 번씩 읽어 메모리에서 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    public static final Set<Integer> PERIODS = Set.of(30, 90, 365);
    /** Anki: 간격 21일 이상이면 mature */
    static final int MATURE_DAYS = 21;
    static final int CALENDAR_DAYS = 365;

    private final UserService userService;
    private final ReviewLogRepository reviewLogRepository;
    private final CardStateRepository cardStateRepository;
    private final CardRepository cardRepository;
    private final Clock clock;

    /**
     * @param deck all / words / grammar
     * @param days 30 / 90 / 365
     */
    public StatsResponse get(UUID userId, String deck, int days) {
        if (!PERIODS.contains(days)) throw new InvalidStatsQueryException();
        List<String> tags = tagsOf(deck);

        User user = userService.getById(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        ZoneId serverZone = clock.getZone();
        ZoneId userZone = StudyDay.zoneOf(user);
        StudyDay day = StudyDay.of(user, now, serverZone);
        LocalDate today = toDate(now, serverZone, userZone);
        LocalDate periodStart = today.minusDays(days - 1);
        LocalDate calendarStart = today.minusDays(CALENDAR_DAYS - 1);

        // 1년치 복습 기록 (달력·연속일·기억률 1년에 필요)
        List<ReviewRow> logs = reviewLogRepository.findRowsSince(
                userId, tags, day.start().minusDays(CALENDAR_DAYS - 1));
        List<Dated> dated = logs.stream().map(r -> {
            var at = r.reviewTime().atZone(serverZone).withZoneSameInstant(userZone);
            return new Dated(at.toLocalDate(), at.getHour(), r);
        }).toList();
        List<Dated> inPeriod = dated.stream().filter(d -> !d.date().isBefore(periodStart)).toList();

        List<CardStateRow> states = cardStateRepository.findRows(userId, tags);
        int level = JlptLevel.forStudy(user.getCurrentLevel());
        long unseen = tags.stream().mapToLong(t -> cardRepository.countUnseen(userId, t, level)).sum();

        return new StatsResponse(
                deck, days, today,
                todayStats(dated, today),
                streak(dated, today, periodStart, days),
                calendar(dated, calendarStart),
                reviews(inPeriod, periodStart, days),
                reviewSummary(inPeriod, days),
                forecast(states, today, days, serverZone, userZone),
                cardCounts(states, unseen),
                histogram(reviewCards(states), INTERVAL_BUCKETS, s -> s.scheduledDays()),
                histogram(reviewCards(states), INTERVAL_BUCKETS, s -> (int) Math.round(s.stability())),
                percentHistogram(reviewCards(states), DIFFICULTY_BUCKETS, s -> (s.difficulty() - 1) / 9 * 100),
                percentHistogram(reviewCards(states), RETRIEVABILITY_BUCKETS, s -> retrievability(s, now) * 100),
                hours(inPeriod),
                buttons(inPeriod),
                retention(dated, today)
        );
    }

    private static List<String> tagsOf(String deck) {
        if ("all".equals(deck)) return List.of(Card.TAG_WORD, Card.TAG_GRAMMAR);
        return StudyKind.fromPath(deck)
                .map(k -> List.of(k.cardTag()))
                .orElseThrow(InvalidStatsQueryException::new);
    }

    private static LocalDate toDate(LocalDateTime t, ZoneId serverZone, ZoneId userZone) {
        return t.atZone(serverZone).withZoneSameInstant(userZone).toLocalDate();
    }

    /** 유저 시간대 기준 날짜·시 */
    private record Dated(LocalDate date, int hour, ReviewRow row) {}

    // ---------------------------------------------------------------- 오늘 · 연속일 · 달력

    private static Today todayStats(List<Dated> dated, LocalDate today) {
        List<ReviewRow> rows = dated.stream().filter(d -> d.date().equals(today)).map(Dated::row).toList();
        long again = rows.stream().filter(StatsService::failed).count();
        List<ReviewRow> mature = rows.stream()
                .filter(r -> r.state() == CardPhase.REVIEW && r.elapsedDays() >= MATURE_DAYS).toList();
        return new Today(
                rows.size(),
                seconds(rows),
                countState(rows, CardPhase.NEW),
                countState(rows, CardPhase.LEARNING),
                countState(rows, CardPhase.REVIEW),
                countState(rows, CardPhase.RELEARNING),
                again,
                rate(rows.size() - again, rows.size()),
                mature.size(),
                rate(mature.stream().filter(r -> !failed(r)).count(), mature.size()));
    }

    private static Streak streak(List<Dated> dated, LocalDate today, LocalDate periodStart, int days) {
        Set<LocalDate> studied = new HashSet<>();
        dated.forEach(d -> studied.add(d.date()));

        LocalDate cursor = studied.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (studied.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }

        int longest = 0;
        int run = 0;
        for (LocalDate d : new TreeSet<>(studied)) {
            run = studied.contains(d.minusDays(1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
        }
        int inPeriod = (int) studied.stream().filter(d -> !d.isBefore(periodStart)).count();
        return new Streak(current, longest, inPeriod, days);
    }

    private static List<DayCount> calendar(List<Dated> dated, LocalDate from) {
        Map<LocalDate, Long> counts = new TreeMap<>();
        dated.stream().filter(d -> !d.date().isBefore(from)).forEach(d -> counts.merge(d.date(), 1L, Long::sum));
        return counts.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList();
    }

    // ---------------------------------------------------------------- 복습 그래프

    private static List<DayReviews> reviews(List<Dated> inPeriod, LocalDate from, int days) {
        Map<LocalDate, List<ReviewRow>> byDate = new HashMap<>();
        inPeriod.forEach(d -> byDate.computeIfAbsent(d.date(), k -> new ArrayList<>()).add(d.row()));

        List<DayReviews> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            List<ReviewRow> rows = byDate.getOrDefault(date, List.of());
            long newCards = countState(rows, CardPhase.NEW);
            result.add(new DayReviews(date,
                    newCards + countState(rows, CardPhase.LEARNING),
                    countState(rows, CardPhase.REVIEW),
                    countState(rows, CardPhase.RELEARNING),
                    newCards,
                    seconds(rows)));
        }
        return result;
    }

    private static ReviewSummary reviewSummary(List<Dated> inPeriod, int days) {
        long total = inPeriod.size();
        int studiedDays = (int) inPeriod.stream().map(Dated::date).distinct().count();
        return new ReviewSummary(
                total,
                studiedDays,
                studiedDays == 0 ? 0 : round1((double) total / studiedDays),
                round1((double) total / days),
                seconds(inPeriod.stream().map(Dated::row).toList()));
    }

    // ---------------------------------------------------------------- 미래 예정 · 카드 수

    private static Forecast forecast(List<CardStateRow> states, LocalDate today, int days,
                                     ZoneId serverZone, ZoneId userZone) {
        long[] due = new long[days];
        long overdue = 0;
        double load = 0;
        for (CardStateRow s : states) {
            long offset = ChronoUnit.DAYS.between(today, toDate(s.due(), serverZone, userZone));
            if (offset < 0) {
                overdue++;
                offset = 0;
            }
            if (offset < days) due[(int) offset]++;
            if (s.state() == CardPhase.REVIEW) load += 1.0 / Math.max(1, s.scheduledDays());
        }
        List<Long> list = Arrays.stream(due).boxed().toList();
        return new Forecast(list, days > 1 ? due[1] : 0, overdue, Math.round(load));
    }

    private static CardCounts cardCounts(List<CardStateRow> states, long unseen) {
        long young = reviewCards(states).stream().filter(s -> s.scheduledDays() < MATURE_DAYS).count();
        long mature = reviewCards(states).size() - young;
        return new CardCounts(unseen,
                states.stream().filter(s -> s.state() == CardPhase.LEARNING).count(),
                states.stream().filter(s -> s.state() == CardPhase.RELEARNING).count(),
                young, mature);
    }

    private static List<CardStateRow> reviewCards(List<CardStateRow> states) {
        return states.stream().filter(s -> s.state() == CardPhase.REVIEW).toList();
    }

    // ---------------------------------------------------------------- 분포 (간격 · 안정도 · 난이도 · 기억률)

    /** 일 단위 구간 [min, max] */
    private record Range(String label, int min, int max) {}

    private static final List<Range> INTERVAL_BUCKETS = List.of(
            new Range("1일", 0, 1),
            new Range("2일", 2, 2),
            new Range("3일", 3, 3),
            new Range("4–7일", 4, 7),
            new Range("1–2주", 8, 14),
            new Range("2주–1개월", 15, 30),
            new Range("1–3개월", 31, 90),
            new Range("3–6개월", 91, 180),
            new Range("6개월–1년", 181, 365),
            new Range("1년+", 366, Integer.MAX_VALUE));

    private static final List<Range> DIFFICULTY_BUCKETS = List.of(
            new Range("0–10%", 0, 9), new Range("10–20%", 10, 19), new Range("20–30%", 20, 29),
            new Range("30–40%", 30, 39), new Range("40–50%", 40, 49), new Range("50–60%", 50, 59),
            new Range("60–70%", 60, 69), new Range("70–80%", 70, 79), new Range("80–90%", 80, 89),
            new Range("90–100%", 90, 100));

    private static final List<Range> RETRIEVABILITY_BUCKETS = List.of(
            new Range("50% 미만", 0, 49), new Range("50–60%", 50, 59), new Range("60–70%", 60, 69),
            new Range("70–80%", 70, 79), new Range("80–90%", 80, 89), new Range("90–100%", 90, 100));

    private static Histogram histogram(List<CardStateRow> cards, List<Range> ranges, ToIntFunction<CardStateRow> value) {
        long[] counts = new long[ranges.size()];
        for (CardStateRow c : cards) {
            int v = value.applyAsInt(c);
            for (int i = 0; i < ranges.size(); i++) {
                if (v >= ranges.get(i).min() && v <= ranges.get(i).max()) {
                    counts[i]++;
                    break;
                }
            }
        }
        IntSummaryStatistics stats = cards.stream().mapToInt(value).summaryStatistics();
        return toHistogram(ranges, counts, cards.isEmpty() ? null : round1(stats.getAverage()),
                cards.isEmpty() ? null : (double) stats.getMax());
    }

    private static Histogram percentHistogram(List<CardStateRow> cards, List<Range> ranges,
                                              ToDoubleFunction<CardStateRow> percent) {
        Histogram h = histogram(cards, ranges, c -> (int) Math.floor(Math.min(100, Math.max(0, percent.applyAsDouble(c)))));
        Double avg = cards.isEmpty() ? null : round1(cards.stream().mapToDouble(percent).average().orElse(0));
        return new Histogram(h.buckets(), avg, null);
    }

    private static Histogram toHistogram(List<Range> ranges, long[] counts, Double average, Double max) {
        List<Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) buckets.add(new Bucket(ranges.get(i).label(), counts[i]));
        return new Histogram(buckets, average, max);
    }

    private static final FsrsScheduler SCHEDULER = new FsrsScheduler(FsrsSettings.defaults());

    /** 지금 이 카드를 기억하고 있을 확률 (FSRS) */
    private static double retrievability(CardStateRow s, LocalDateTime now) {
        FsrsCard card = new FsrsCard(s.state(), null, s.stability(), s.difficulty(), s.due(), s.lastReview());
        return SCHEDULER.retrievability(card, now);
    }

    // ---------------------------------------------------------------- 시간대 · 버튼 · 기억률

    private static List<HourStat> hours(List<Dated> inPeriod) {
        long[] count = new long[24];
        long[] pass = new long[24];
        for (Dated d : inPeriod) {
            int h = d.hour();
            count[h]++;
            if (!failed(d.row())) pass[h]++;
        }
        List<HourStat> result = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) result.add(new HourStat(h, count[h], rate(pass[h], count[h])));
        return result;
    }

    private static List<ButtonStat> buttons(List<Dated> inPeriod) {
        Map<String, long[]> groups = new LinkedHashMap<>();
        groups.put("LEARNING", new long[4]);
        groups.put("YOUNG", new long[4]);
        groups.put("MATURE", new long[4]);
        for (Dated d : inPeriod) {
            ReviewRow r = d.row();
            String group = r.state() != CardPhase.REVIEW ? "LEARNING"
                    : r.elapsedDays() >= MATURE_DAYS ? "MATURE" : "YOUNG";
            groups.get(group)[r.rating() - 1]++;
        }
        return groups.entrySet().stream().map(e -> {
            long[] c = e.getValue();
            long total = Arrays.stream(c).sum();
            return new ButtonStat(e.getKey(), Arrays.stream(c).boxed().toList(), rate(total - c[0], total));
        }).toList();
    }

    private static List<RetentionRow> retention(List<Dated> dated, LocalDate today) {
        return List.of(
                retentionRow("오늘", dated, today, today),
                retentionRow("어제", dated, today.minusDays(1), today.minusDays(1)),
                retentionRow("지난 7일", dated, today.minusDays(6), today),
                retentionRow("지난 30일", dated, today.minusDays(29), today),
                retentionRow("지난 1년", dated, today.minusDays(CALENDAR_DAYS - 1), today));
    }

    private static RetentionRow retentionRow(String label, List<Dated> dated, LocalDate from, LocalDate to) {
        long yp = 0, yf = 0, mp = 0, mf = 0;
        for (Dated d : dated) {
            ReviewRow r = d.row();
            if (r.state() != CardPhase.REVIEW || d.date().isBefore(from) || d.date().isAfter(to)) continue;
            boolean mature = r.elapsedDays() >= MATURE_DAYS;
            boolean fail = failed(r);
            if (mature) {
                if (fail) mf++; else mp++;
            } else {
                if (fail) yf++; else yp++;
            }
        }
        return new RetentionRow(label, yp, yf, mp, mf);
    }

    // ---------------------------------------------------------------- 보조

    private static boolean failed(ReviewRow r) {
        return r.rating() == 1;
    }

    private static long countState(List<ReviewRow> rows, CardPhase state) {
        return rows.stream().filter(r -> r.state() == state).count();
    }

    private static long seconds(List<ReviewRow> rows) {
        return rows.stream().mapToLong(r -> r.durationMs() == null ? 0 : r.durationMs()).sum() / 1000;
    }

    private static Double rate(long pass, long total) {
        return total == 0 ? null : round1(100.0 * pass / total);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
