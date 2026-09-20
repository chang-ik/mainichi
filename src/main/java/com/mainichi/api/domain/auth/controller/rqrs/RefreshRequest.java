package com.mainichi.api.domain.auth.controller.rqrs;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank String refreshToken
) {
}