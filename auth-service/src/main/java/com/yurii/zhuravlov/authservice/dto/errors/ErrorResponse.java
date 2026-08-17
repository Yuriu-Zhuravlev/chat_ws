package com.yurii.zhuravlov.authservice.dto.errors;

public record ErrorResponse(
        String error,
        String message,
        String path,
        int status
) {}