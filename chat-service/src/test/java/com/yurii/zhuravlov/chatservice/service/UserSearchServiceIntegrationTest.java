package com.yurii.zhuravlov.chatservice.service;

import com.yurii.zhuravlov.chatservice.IntegrationTestBase;
import com.yurii.zhuravlov.chatservice.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.yurii.zhuravlov.chatservice.constants.Constants.PAGE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class UserSearchServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    UserSearchService userSearchService;

    @Test
    void findsByCaseInsensitiveSubstringAndExcludesRequester() {
        givenUser(1L, "Requester");
        givenUser(2L, "vasyl");
        givenUser(3L, "VASYLINA");
        givenUser(4L, "petro");

        List<UserResponse> found = userSearchService.search("vAsYl", 1L, 0);

        assertThat(found).extracting(UserResponse::username)
                .containsExactly("VASYLINA", "vasyl");
    }

    @Test
    void excludesRequesterEvenWhenTheyMatch() {
        givenUser(1L, "vasyl");
        givenUser(2L, "vasyl2");

        List<UserResponse> found = userSearchService.search("vasyl", 1L, 0);

        assertThat(found).extracting(UserResponse::id).containsExactly(2L);
    }

    @Test
    void paginatesByUsername() {
        givenUser(1L, "requester");
        for (int i = 0; i < PAGE_SIZE + 3; i++) {
            givenUser(100L + i, "user%02d".formatted(i));
        }

        assertThat(userSearchService.search("user", 1L, 0)).hasSize(PAGE_SIZE);
        assertThat(userSearchService.search("user", 1L, 1)).hasSize(3);
    }

    @Test
    void returnsEmptyPageBeyondResults() {
        givenUser(1L, "requester");
        givenUser(2L, "vasyl");

        assertThat(userSearchService.search("vasyl", 1L, 5)).isEmpty();
    }

    @Test
    void treatsWildcardsAsLiteralCharacters() {
        givenUser(1L, "requester");
        givenUser(2L, "axb");

        assertThat(userSearchService.search("a_b", 1L, 0)).isEmpty();
    }

    @Test
    void treatsPercentAsLiteralCharacter() {
        givenUser(1L, "requester");
        givenUser(2L, "vasyl");

        assertThat(userSearchService.search("%%", 1L, 0)).isEmpty();
    }
}