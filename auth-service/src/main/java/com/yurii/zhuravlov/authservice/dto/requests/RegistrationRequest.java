package com.yurii.zhuravlov.authservice.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Username required")
        @Size(min = 3, max = 32, message = "Username must be 3-32 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username may contain only letters, digits, dot, dash and underscore")
        String username,

        @NotBlank(message = "Password required")
        @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Password may contain only letters, digits, dot, dash and underscore")
        String password
) {
}
