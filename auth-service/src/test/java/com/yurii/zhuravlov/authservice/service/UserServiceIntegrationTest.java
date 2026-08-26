package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.IntegrationTestBase;
import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.UserAlreadyExists;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    UserService userService;

    @Test
    void shouldStoreHashedPasswordNotPlaintext() {
        userService.register(new RegistrationRequest("vasyl", "password123"));

        User saved = userRepository.findByUsernameIgnoreCase("vasyl").orElseThrow();

        assertThat(saved.getPasswordHash())
                .isNotEqualTo("password123")
                .startsWith("{bcrypt}$2a$");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void shouldRejectUsernameDifferingOnlyByCase() {
        userService.register(new RegistrationRequest("vasyl", "password123"));

        assertThatThrownBy(() ->
                userService.register(new RegistrationRequest("VASYL", "password123")))
                .isInstanceOf(UserAlreadyExists.class);

        assertThat(userRepository.count()).isOne();
    }

    @Test
    void shouldPreserveOriginalCaseForDisplay() {
        userService.register(new RegistrationRequest("VaSyL", "password123"));

        assertThat(userRepository.findByUsernameIgnoreCase("vasyl").orElseThrow()
                .getUsername()).isEqualTo("VaSyL");
    }

    @Test
    void shouldRejectDuplicateWhenPreCheckIsBypassed() {
        userService.register(new RegistrationRequest("vasyl", "password123"));

        User racing = new User("{bcrypt}$2a$10$hash", "Vasyl");

        assertThatThrownBy(() -> userRepository.saveAndFlush(racing))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldSetCreatedAtOnPersist() {
        Instant before = Instant.now();

        userService.register(new RegistrationRequest("vasyl", "password123"));

        User saved = userRepository.findByUsernameIgnoreCase("vasyl").orElseThrow();
        assertThat(saved.getCreatedAt()).isNotNull().isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void registrationShouldWriteOutboxEvent() {
        userService.register(new RegistrationRequest("vasyl", "password123"));

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventType()).isEqualTo("UserRegistered");
                    assertThat(e.getAggregateType()).isEqualTo("User");
                    assertThat(e.getPublishedAt()).isNull();
                });
    }

    @Test
    void failedRegistrationShouldNotLeaveOutboxEvent() {
        userService.register(new RegistrationRequest("vasyl", "password123"));

        assertThatThrownBy(() ->
                userService.register(new RegistrationRequest("VASYL", "password123")))
                .isInstanceOf(UserAlreadyExists.class);

        assertThat(outboxRepository.count()).isOne();   // тільки від першої реєстрації
    }
}