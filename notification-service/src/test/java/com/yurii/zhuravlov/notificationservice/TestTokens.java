package com.yurii.zhuravlov.notificationservice;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class TestTokens {

    private final RSAKey testRsaKey;

    public String valid(long userId) {
        return signed(userId, Instant.now().plus(Duration.ofMinutes(15)));
    }

    public String expiringIn(long userId, Duration ttl) {
        return signed(userId, Instant.now().plus(ttl));
    }

    private String signed(long userId, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .issuer("auth-service")
                    .audience("notification-service")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiresAt))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(testRsaKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(testRsaKey));

            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test token", e);
        }
    }
}