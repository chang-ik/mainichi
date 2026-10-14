package com.mainichi.api.domain.stats.service;

import com.mainichi.api.domain.study.fsrs.CardPhase;

import java.time.LocalDateTime;

/**
 * 통계 계산용 복습 기록 한 줄.
 *
 * @param state       복습 "전" 단계
 * @param elapsedDays 지난 복습에서 지난 일수 (Anki 의 young/mature 구분에 쓴다)
 * @param durationMs  걸린 시간. 기록 전 데이터는 null
 */
public record ReviewRow(LocalDateTime reviewTime, int rating, CardPhase state, int elapsedDays, Integer durationMs) {}
