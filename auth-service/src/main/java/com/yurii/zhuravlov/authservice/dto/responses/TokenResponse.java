package com.yurii.zhuravlov.authservice.dto.responses;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}