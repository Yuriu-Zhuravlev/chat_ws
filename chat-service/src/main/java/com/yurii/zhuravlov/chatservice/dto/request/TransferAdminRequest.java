package com.yurii.zhuravlov.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record TransferAdminRequest(@NotNull Long userId) {}