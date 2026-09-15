package com.yurii.zhuravlov.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(@NotNull Long messageId) {}