package com.yurii.zhuravlov.authservice.controller;

import com.yurii.zhuravlov.authservice.dto.requests.LoginRequest;
import com.yurii.zhuravlov.authservice.dto.requests.RefreshTokenRequest;
import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.dto.responses.TokenResponse;
import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.mapper.TokenResponseMapper;
import com.yurii.zhuravlov.authservice.service.AuthService;
import com.yurii.zhuravlov.authservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthService authService;
    private final TokenResponseMapper tokenResponseMapper;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegistrationRequest request) {
        userService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return tokenResponseMapper.toResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponseMapper.toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request){
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getById(Long.valueOf(Objects.requireNonNull(jwt.getSubject())));
    }
}
