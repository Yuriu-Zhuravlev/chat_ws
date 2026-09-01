package com.yurii.zhuravlov.chatservice.controller;

import com.yurii.zhuravlov.chatservice.security.CurrentUserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WhoAmIController {

    @GetMapping("/api/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt, @CurrentUserId Long userId) {
        return Map.of(
                "userId", userId,
                "username", jwt.getClaimAsString("username"),
                "issuer", jwt.getClaimAsString("iss"),
                "audience", jwt.getAudience()
        );
    }
}