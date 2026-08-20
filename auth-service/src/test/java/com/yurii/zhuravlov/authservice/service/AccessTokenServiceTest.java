package com.yurii.zhuravlov.authservice.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.yurii.zhuravlov.authservice.config.properties.JwtProperties;
import com.yurii.zhuravlov.authservice.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccessTokenServiceTest {

    private AccessTokenService service;
    private JwtDecoder decoder;
    private final JwtProperties properties = new JwtProperties(
            "auth-service", Duration.ofMinutes(15), Duration.ofDays(30),
            "test-key", null, null, List.of("auth-service", "chat-service", "notification-service"));

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey(pair.getPrivate())
                .keyID("test-key")
                .build();

        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        service = new AccessTokenService(new NimbusJwtEncoder(source), properties);
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
    }

    @Test
    void shouldPutUserIdInSubject() {
        User user = userWithId(42L, "vasyl");

        Jwt jwt = decoder.decode(service.issue(user));

        assertThat(jwt.getSubject()).isEqualTo("42");
    }

    @Test
    void shouldIncludeUsernameClaim() {
        Jwt jwt = decoder.decode(service.issue(userWithId(42L, "vasyl")));
        assertThat(jwt.getClaimAsString("username")).isEqualTo("vasyl");
    }

    @Test
    void shouldTargetChatAndNotificationServices() {
        Jwt jwt = decoder.decode(service.issue(userWithId(42L, "vasyl")));
        assertThat(jwt.getAudience())
                .containsExactlyInAnyOrder("auth-service", "chat-service", "notification-service");
    }

    @Test
    void shouldExpireAccordingToConfiguredTtl() {
        Jwt jwt = decoder.decode(service.issue(userWithId(42L, "vasyl")));

        assertNotNull(jwt.getIssuedAt());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void shouldSetKeyIdInHeaderForRotation() {
        Jwt jwt = decoder.decode(service.issue(userWithId(42L, "vasyl")));
        assertThat(jwt.getHeaders()).containsEntry("kid", "test-key");
    }

    @Test
    void eachTokenShouldHaveUniqueJti() {
        User user = userWithId(42L, "vasyl");
        assertThat(decoder.decode(service.issue(user)).getId())
                .isNotEqualTo(decoder.decode(service.issue(user)).getId());
    }

    private User userWithId(Long id, String username) {
        User user = new User("{bcrypt}$2a$10$hash", username);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}