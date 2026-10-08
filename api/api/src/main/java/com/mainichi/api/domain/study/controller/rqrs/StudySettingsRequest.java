package com.mainichi.api.domain.study.controller.rqrs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 하루 학습량 = 하루에 새로 꺼낼 카드 수 */
public record StudySettingsRequest(
        @NotNull @Min(1) @Max(100) Integer newCardsPerDay
) {}
