package com.yurii.zhuravlov.authservice;

import com.yurii.zhuravlov.authservice.entities.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextLoadsTest extends IntegrationTestBase {

    @Test
    void contextLoadsAndSchemaIsMigrated() {
        User user = givenUser("vasyl", "password123");
        assertThat(user.getId()).isNotNull();
        assertThat(userRepository.count()).isEqualTo(1);
    }
}