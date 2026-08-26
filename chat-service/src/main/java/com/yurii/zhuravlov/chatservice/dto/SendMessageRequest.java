package com.yurii.zhuravlov.chatservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank
        @Size(max = 4000, message = "Message must not exceed 4000 characters")
        String content,

        @NotBlank
        @Size(max = 64)
        String clientMessageId
) {}