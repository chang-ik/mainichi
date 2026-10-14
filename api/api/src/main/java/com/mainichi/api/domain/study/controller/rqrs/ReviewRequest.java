package com.mainichi.api.domain.study.controller.rqrs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param rating     1=다시(✕) 2=어려움(△) 3=알맞음(○) 4=쉬움(◎)
 * @param durationMs 카드를 보고 평가하기까지 걸린 시간(ms). 통계용, 없어도 된다
 */
public record ReviewRequest(@NotNull @Min(1) @Max(4) Integer rating, @Min(0) Long durationMs) {}
