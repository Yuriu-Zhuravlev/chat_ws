package com.yurii.zhuravlov.notificationservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WebSocketAuthenticator {

    private final JwtDecoder jwtDecoder;

    public record AuthenticatedUser(Long userId, Instant expiresAt) {}

    /**
     * The decoder validates signature, issuer, audience and timestamps — the same rules
     * the REST services apply, just triggered by a frame instead of a header.
     */
    public AuthenticatedUser authenticate(String token) {
        Jwt jwt = jwtDecoder.decode(token);

        String subject = jwt.getSubject();
        try {
            return new AuthenticatedUser(Long.valueOf(Objects.requireNonNull(subject)), jwt.getExpiresAt());
        } catch (NumberFormatException e) {
            throw new JwtException("JWT subject is not a numeric user id: " + subject, e);
        }
    }
}