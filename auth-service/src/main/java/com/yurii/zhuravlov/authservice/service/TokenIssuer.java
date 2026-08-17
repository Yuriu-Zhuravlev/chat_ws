package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.config.properties.JwtProperties;
import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.entities.RefreshToken;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.repo.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final JwtProperties properties;

    public TokenPair issue(User user, Instant now) {
        String rawRefresh = generateRawToken();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawRefresh))
                .expiresAt(now.plus(properties.refreshTokenTtl()))
                .build();

        refreshTokenRepository.save(token);

        return new TokenPair(accessTokenService.issue(user), rawRefresh);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}