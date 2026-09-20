package com.mainichi.api.domain.user.controller.rqrs;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 20) String nickname,
        String timezone
){}