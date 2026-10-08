package com.mainichi.api.domain.auth.controller.rqrs;

import com.mainichi.api.domain.user.entity.StudyArea;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        UserSummary user
) {
    public record UserSummary(
            String id,
            String nickname,
            String email,
            String provider,
            Integer currentLevel,
            String timezone,
            List<StudyArea> studyAreas
    ) {}
}
