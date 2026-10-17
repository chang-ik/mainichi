package com.mainichi.api.domain.stats.service;

import com.mainichi.api.domain.study.fsrs.CardPhase;

import java.time.LocalDateTime;

/** 통계 계산용 카드 학습 상태 */
public record CardStateRow(CardPhase state, LocalDateTime due, double stability, double difficulty,
                           int scheduledDays, LocalDateTime lastReview) {}
