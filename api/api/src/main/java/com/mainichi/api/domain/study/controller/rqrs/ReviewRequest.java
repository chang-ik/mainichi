package com.mainichi.api.domain.study.controller.rqrs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** rating: 1=다시(✕) 2=어려움(△) 3=알맞음(○) 4=쉬움(◎) */
public record ReviewRequest(@NotNull @Min(1) @Max(4) Integer rating) {}
