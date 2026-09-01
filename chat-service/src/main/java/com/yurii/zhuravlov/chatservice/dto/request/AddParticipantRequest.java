package com.yurii.zhuravlov.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddParticipantRequest(@NotNull Long userId) {}