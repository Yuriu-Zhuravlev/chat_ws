package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.config.properties.JwtProperties;
import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.entities.RefreshToken;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.repo.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenIssuerTest {

    @Nested
    class Sha256Hex {
        @Test
        void sha256HexShouldBeDeterministic() {
            assertThat(TokenIssuer.sha256Hex("abc"))
                    .isEqualTo(TokenIssuer.sha256Hex("abc"));
        }

        @Test
        void sha256HexShouldReturn64HexChars() {
            assertThat(TokenIssuer.sha256Hex("any value"))
                    .hasSize(64)
                    .matches("^[0-9a-f]{64}$");
        }

        @Test
        void sha256HexShouldMatchKnownVector() {
            assertThat(TokenIssuer.sha256Hex("abc")).isEqualTo(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        }

        @Test
        void differentInputsShouldProduceDifferentHashes() {
            assertThat(TokenIssuer.sha256Hex("abc"))
                    .isNotEqualTo(TokenIssuer.sha256Hex("abd"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Issue {
        @Mock
        RefreshTokenRepository refreshTokenRepository;
        @Mock
        AccessTokenService accessTokenService;

        private final JwtProperties properties = new JwtProperties(
                "auth-service", Duration.ofMinutes(15), Duration.ofDays(30),
                "test-key", null, null, List.of("auth-service", "chat-service", "notification-service"));

        private TokenIssuer tokenIssuer;

        @BeforeEach
        void setUp() {
            tokenIssuer = new TokenIssuer(refreshTokenRepository, accessTokenService, properties);
        }

        @Captor
        ArgumentCaptor<RefreshToken> tokenCaptor;

        private final User user = new User("{bcrypt}$2a$10$hash", "vasyl");
        private final Instant now = Instant.parse("2026-08-05T10:00:00Z");

        @Test
        void shouldReturnRawTokenButPersistOnlyItsHash() {
            when(accessTokenService.issue(user)).thenReturn("jwt-value");

            TokenPair pair = tokenIssuer.issue(user, now);

            verify(refreshTokenRepository).save(tokenCaptor.capture());
            RefreshToken saved = tokenCaptor.getValue();

            assertThat(saved.getTokenHash())
                    .isEqualTo(TokenIssuer.sha256Hex(pair.refreshToken()));
            assertThat(saved.getTokenHash()).isNotEqualTo(pair.refreshToken());
        }

        @Test
        void refreshTokenShouldBe43UrlSafeChars() {
            TokenPair pair = tokenIssuer.issue(user, now);

            assertThat(pair.refreshToken())
                    .hasSize(43)
                    .matches("^[A-Za-z0-9_-]+$");
        }

        @Test
        void shouldSetExpiryFromProvidedInstantAndTtl() {
            tokenIssuer.issue(user, now);

            verify(refreshTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getExpiresAt())
                    .isEqualTo(now.plus(Duration.ofDays(30)));
        }

        @Test
        void eachCallShouldProduceUniqueToken() {
            Set<String> tokens = IntStream.range(0, 100)
                    .mapToObj(i -> tokenIssuer.issue(user, now).refreshToken())
                    .collect(Collectors.toSet());

            assertThat(tokens).hasSize(100);
        }
    }


}