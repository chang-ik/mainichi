package com.mainichi.api.domain.study.fsrs;

import java.time.Duration;

/**
 * 한 번 복습한 결과.
 *
 * @param card          복습 후 카드 상태
 * @param interval      다음 복습까지 간격
 * @param elapsedDays   직전 복습 이후 지난 일수 (처음이면 0)
 * @param scheduledDays 다음 복습까지 일수 (학습 단계처럼 하루 미만이면 0)
 */
public record ReviewOutcome(FsrsCard card, Duration interval, int elapsedDays, int scheduledDays) {}
