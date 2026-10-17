package com.mainichi.api.domain.quiz.controller.rqrs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** answers: 문제 순서대로 고른 보기 번호 (0~3) */
public record SubmitQuizRequest(
        @NotNull @Size(min = 2, max = 2) List<@NotNull @Min(0) @Max(3) Integer> answers
) {}
