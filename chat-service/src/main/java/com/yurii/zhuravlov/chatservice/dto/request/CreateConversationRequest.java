package com.yurii.zhuravlov.chatservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateConversationRequest(
        @NotBlank @Size(max = 128) String title,
        @NotNull @Size(max = 99) Set<@NotNull Long> participantIds
) {
    public CreateConversationRequest {
        participantIds = (participantIds == null) ? Set.of() : Set.copyOf(participantIds);
    }
}