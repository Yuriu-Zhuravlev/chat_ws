package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.IntegrationTestBase;
import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.dto.requests.LoginRequest;
import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.exceptions.RefreshTokenException;
import com.yurii.zhuravlov.authservice.exceptions.TokenTheftException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class AuthServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    AuthService authService;
    @Autowired
    UserService userService;
    @Autowired
    RefreshGraceCache refreshGraceCache;

    @Test
    void fullRotationCycleShouldInvalidateOldToken() {
        userService.register(new RegistrationRequest("vasyl", "password123"));

        TokenPair first = authService.login(new LoginRequest("vasyl", "password123"));
        TokenPair second = authService.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());

        assertThatNoException().isThrownBy(() -> authService.refresh(second.refreshToken()));
    }

    @Test
    void loginShouldBeCaseInsensitiveForUsername() {
        userService.register(new RegistrationRequest("VaSyL", "password123"));

        assertThatNoException().isThrownBy(() ->
                authService.login(new LoginRequest("vasyl", "password123")));
    }

    @Test
    void secondRefreshWithinGraceShouldReturnCachedPair() {
        userService.register(new RegistrationRequest("vasyl", "password123"));
        TokenPair issued = authService.login(new LoginRequest("vasyl", "password123"));

        TokenPair rotated = authService.refresh(issued.refreshToken());
        TokenPair repeated = authService.refresh(issued.refreshToken());

        assertThat(repeated).isEqualTo(rotated);
    }

    @Test
    void reuseOutsideGraceShouldTerminateSessionEndToEnd() {
        userService.register(new RegistrationRequest("vasyl", "password123"));
        TokenPair first = authService.login(new LoginRequest("vasyl", "password123"));
        String firstHash = TokenIssuer.sha256Hex(first.refreshToken());

        TokenPair second = authService.refresh(first.refreshToken());

        ageUsedAt(firstHash, Duration.ofMinutes(5));
        evictGrace(firstHash);

        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOf(TokenTheftException.class);

        assertThatThrownBy(() -> authService.refresh(second.refreshToken()))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void logoutShouldPreventFurtherRefresh() {
        userService.register(new RegistrationRequest("vasyl", "password123"));
        TokenPair issued = authService.login(new LoginRequest("vasyl", "password123"));

        authService.logout(issued.refreshToken());

        assertThatThrownBy(() -> authService.refresh(issued.refreshToken()))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void logoutShouldBeIdempotent() {
        userService.register(new RegistrationRequest("vasyl", "password123"));
        TokenPair issued = authService.login(new LoginRequest("vasyl", "password123"));

        authService.logout(issued.refreshToken());

        assertThatNoException().isThrownBy(() -> authService.logout(issued.refreshToken()));
        assertThatNoException().isThrownBy(() -> authService.logout("never-existed"));
    }
}