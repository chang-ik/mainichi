package com.mainichi.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트에서 시간을 앞으로 돌릴 수 있는 시계. @Import(MutableClock.Config.class) 로 쓴다. */
public class MutableClock extends Clock {

    private static final Instant START = Instant.parse("2026-09-29T00:00:00Z"); // 서울 09:00

    private Instant instant = START;
    private final ZoneId zone = ZoneId.of("Asia/Seoul");

    public void advance(Duration d) {
        instant = instant.plus(d);
    }

    public void reset() {
        instant = START;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }
}
