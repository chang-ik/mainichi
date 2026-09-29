package com.mainichi.api.domain.auth.controller.rqrs;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String provider,
        @NotBlank String code
) {}
