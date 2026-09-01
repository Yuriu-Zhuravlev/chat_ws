package com.yurii.zhuravlov.chatservice.dto.errors;

public record ErrorResponse(
        String error,
        String message,
        String path,
        int status
) {}