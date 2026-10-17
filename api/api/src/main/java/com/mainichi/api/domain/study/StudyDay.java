package com.mainichi.api.domain.study;

import com.mainichi.api.domain.user.entity.User;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유저 시간대 기준 "오늘"을 서버 시간(LocalDateTime) 범위로 나타낸다. 하루 기준은 유저 timezone 의 자정이다.
 *
 * @param start        오늘 시작 (포함)
 * @param endExclusive 내일 시작 (미포함)
 */
public record StudyDay(LocalDateTime start, LocalDateTime endExclusive) {

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Seoul");

    public static StudyDay of(User user, LocalDateTime now, ZoneId serverZone) {
        ZoneId userZone = zoneOf(user);
        LocalDate today = now.atZone(serverZone).withZoneSameInstant(userZone).toLocalDate();
        LocalDateTime start = today.atStartOfDay(userZone).withZoneSameInstant(serverZone).toLocalDateTime();
        LocalDateTime end = today.plusDays(1).atStartOfDay(userZone).withZoneSameInstant(serverZone).toLocalDateTime();
        return new StudyDay(start, end);
    }

    public LocalDateTime lastMoment() {
        return endExclusive.minusNanos(1);
    }

    /** 유저 timezone. 잘못된 값이면 서울 */
    public static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return FALLBACK_ZONE;
        }
    }
}
