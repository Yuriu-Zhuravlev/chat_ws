package com.yurii.zhuravlov.chatservice;

import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.entities.User;
import com.yurii.zhuravlov.chatservice.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    ConversationService conversationService;

    @Test
    void listDoesNotDegradeIntoNPlusOne() {
        User me = givenUser(1L, "me");
        for (int i = 0; i < 10; i++) {
            User peer = givenUser(100L + i, "peer" + i);
            conversationService.create(me.getId(),
                    new CreateConversationRequest("chat " + i, Set.of(peer.getId())));
        }

        long queries = countQueries(() -> conversationService.list(me.getId(), 0));

        assertThat(queries).isEqualTo(2);
    }
}