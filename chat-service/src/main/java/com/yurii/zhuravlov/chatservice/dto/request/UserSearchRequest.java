package com.yurii.zhuravlov.chatservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserSearchRequest(
        @NotBlank @Size(min = 2, max = 32) String query,
        @Min(0) Integer page
) {
        public UserSearchRequest {
                page = (page == null) ? 0 : page;
        }
}