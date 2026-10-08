package com.mainichi.api.domain.user.controller.rqrs;

import com.mainichi.api.domain.user.entity.StudyArea;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateProfileRequest(
        @Size(min = 2, max = 20) String nickname,
        String timezone,
        // 0=기초, 1~5=N1~N5 (JlptLevel)
        @Min(0) @Max(5) Integer currentLevel,
        // 단어(WORD)는 항상 포함. null 이면 변경 없음
        Set<StudyArea> studyAreas
){}
