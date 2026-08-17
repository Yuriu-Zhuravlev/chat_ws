package com.yurii.zhuravlov.authservice.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token required")
        @Size(max = 512)
        String refreshToken
) {}