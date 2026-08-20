package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.IntegrationTestBase;
import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.UserAlreadyExists;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

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
    void shouldFindByPartialMatchRegardlessOfCase() {
        givenUser("VasylPetrenko", "password123");
        givenUser("ivan", "password123");

        assertThat(userService.search("vasyl", 999L, 0))
                .extracting(UserResponse::username)
                .containsExactly("VasylPetrenko");
    }

    @Test
    void shouldExcludeRequesterFromResults() {
        User me = givenUser("vasyl", "password123");
        givenUser("vasylina", "password123");

        assertThat(userService.search("vasyl", me.getId(), 0))
                .extracting(UserResponse::username)
                .containsExactly("vasylina");
    }

    @Test
    void shouldReturnEmptyListWhenNothingMatches() {
        givenUser("vasyl", "password123");

        assertThat(userService.search("zzzz", 999L, 0)).isEmpty();
    }

    @Test
    void shouldPaginateWithStableOrder() {
        for (int i = 0; i < 25; i++) {
            givenUser("user%02d".formatted(i), "password123");
        }

        List<UserResponse> first = userService.search("user", 999L, 0);
        List<UserResponse> second = userService.search("user", 999L, 1);

        assertThat(first).hasSize(20);
        assertThat(second).hasSize(5);
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    @Test
    void shouldNotLeakPasswordHash() {
        givenUser("vasyl", "password123");

        assertThat(userService.search("vasyl", 999L, 0))
                .first()
                .satisfies(r -> {
                    assertThat(r.id()).isNotNull();
                    assertThat(r.username()).isEqualTo("vasyl");
                });
    }
}