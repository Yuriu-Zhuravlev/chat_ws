package com.yurii.zhuravlov.authservice.controller;

import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.service.UserService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getById(Long.valueOf(Objects.requireNonNull(jwt.getSubject())));
    }

    @GetMapping("/search")
    public List<UserResponse> search(
            @RequestParam @Size(min = 2, max = 32) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.search(query, Long.valueOf(Objects.requireNonNull(jwt.getSubject())), page);
    }
}