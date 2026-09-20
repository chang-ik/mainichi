package com.mainichi.api.domain.auth.controller.rqrs;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}
