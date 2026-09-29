package com.mainichi.api.domain.auth.controller.rqrs;

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
            String timezone
    ) {}
}
