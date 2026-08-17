package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.config.properties.JwtProperties;
import com.yurii.zhuravlov.authservice.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public String issue(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .audience(List.of("chat-service", "notification-service"))
                .claim("username", user.getUsername())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.keyId())
                .build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}