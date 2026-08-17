package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.dto.requests.LoginRequest;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.TokenTheftException;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    SessionService sessionService;
    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock RefreshGraceCache refreshGraceCache;

    @InjectMocks
    AuthService authService;

    @Test
    void shouldInvokeEncoderEvenWhenUserDoesNotExist() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("pw"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "pw")))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder).matches(eq("pw"), anyString());
        verifyNoInteractions(sessionService);
    }

    @Test
    void shouldRejectWrongPasswordWithSameMessageAsMissingUser() {
        User user = new User("{bcrypt}$2a$10$realhash", "vasyl");
        when(userRepository.findByUsernameIgnoreCase("vasyl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "{bcrypt}$2a$10$realhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("vasyl", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void shouldStartSessionOnValidCredentials() {
        User user = new User("{bcrypt}$2a$10$realhash", "vasyl");
        TokenPair expected = new TokenPair("jwt", "refresh");
        when(userRepository.findByUsernameIgnoreCase("vasyl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "{bcrypt}$2a$10$realhash")).thenReturn(true);
        when(sessionService.startNewSession(user)).thenReturn(expected);

        assertThat(authService.login(new LoginRequest("vasyl", "correct"))).isEqualTo(expected);
    }

    @Test
    void shouldReturnCachedPairWithoutTouchingDatabase() {
        TokenPair cached = new TokenPair("cached-jwt", "cached-refresh");
        String hash = TokenIssuer.sha256Hex("raw-token");
        when(refreshGraceCache.find(hash)).thenReturn(Optional.of(cached));

        assertThat(authService.refresh("raw-token")).isEqualTo(cached);

        verifyNoInteractions(sessionService);
        verify(refreshGraceCache, never()).put(any(), any());
    }

    @Test
    void shouldCacheNewPairUnderOldTokenHash() {
        String oldHash = TokenIssuer.sha256Hex("old-token");
        TokenPair fresh = new TokenPair("new-jwt", "new-refresh");
        when(refreshGraceCache.find(oldHash)).thenReturn(Optional.empty());
        when(sessionService.refreshSession(oldHash)).thenReturn(fresh);

        authService.refresh("old-token");

        verify(refreshGraceCache).put(oldHash, fresh);
    }

    @Test
    void shouldNotCacheWhenRefreshFails() {
        String hash = TokenIssuer.sha256Hex("bad-token");
        when(refreshGraceCache.find(hash)).thenReturn(Optional.empty());
        when(sessionService.refreshSession(hash)).thenThrow(new TokenTheftException("theft"));

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(TokenTheftException.class);

        verify(refreshGraceCache, never()).put(any(), any());
    }
}