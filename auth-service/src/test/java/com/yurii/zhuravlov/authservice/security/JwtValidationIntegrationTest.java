package com.yurii.zhuravlov.authservice.security;

import com.yurii.zhuravlov.authservice.IntegrationTestBase;
import com.yurii.zhuravlov.authservice.config.properties.JwtProperties;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.service.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtValidationIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtProperties properties;
    @Autowired AccessTokenService accessTokenService;

    @Test
    void shouldAcceptValidToken() throws Exception {
        User user = givenUser("vasyl", "password123");

        mockMvc.perform(get("/api/users/me")
                        .header(AUTHORIZATION, "Bearer " + accessTokenService.issue(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("vasyl"));
    }

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWithWrongIssuer() throws Exception {
        User user = givenUser("vasyl", "password123");
        String token = tokenFor(user, claims -> claims.issuer("evil-service"));

        mockMvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWithoutRequiredAudience() throws Exception {
        User user = givenUser("vasyl", "password123");
        String token = tokenFor(user, claims -> claims.audience(List.of("some-other-service")));

        mockMvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        User user = givenUser("vasyl", "password123");
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String token = tokenFor(user, claims -> claims
                .issuedAt(past)
                .expiresAt(past.plus(Duration.ofMinutes(15))));

        mockMvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWithTamperedSignature() throws Exception {
        User user = givenUser("vasyl", "password123");
        String valid = accessTokenService.issue(user);
        String tampered = valid.substring(0, valid.lastIndexOf('.') + 1) + "AAAAAAAA";

        mockMvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenFor(User user, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .audience(properties.audiences())
                .claim("username", user.getUsername());

        customizer.accept(builder);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.keyId())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, builder.build()))
                .getTokenValue();
    }
}