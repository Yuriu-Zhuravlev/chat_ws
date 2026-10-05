package com.yurii.zhuravlov.notificationservice;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

/**
 * Replaces the JWKS-backed decoder with one built from a key pair generated in the test,
 * so no auth-service is needed. The validator chain is the same as production: the test
 * then also covers issuer and audience checks, not just the socket protocol.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {

    @Bean
    public RSAKey testRsaKey() throws Exception {
        return new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder(RSAKey testRsaKey,
                                     @Value("${app.jwt.issuer}") String issuer,
                                     @Value("${app.jwt.audience}") String audience) throws Exception {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(testRsaKey.toRSAPublicKey())
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud.contains(audience))
        ));

        return decoder;
    }
}