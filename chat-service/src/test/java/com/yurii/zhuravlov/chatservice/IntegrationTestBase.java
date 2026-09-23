package com.yurii.zhuravlov.chatservice;

import com.yurii.zhuravlov.chatservice.entities.OutboxEvent;
import com.yurii.zhuravlov.chatservice.entities.User;
import com.yurii.zhuravlov.chatservice.exceptions.ChatServiceException;
import com.yurii.zhuravlov.chatservice.repo.*;
import jakarta.persistence.EntityManagerFactory;
import org.apache.avro.specific.SpecificRecord;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired protected EntityManagerFactory entityManagerFactory;

    @Autowired protected UserRepository userRepository;
    @Autowired protected ConversationRepository conversationRepository;
    @Autowired protected ConversationParticipantRepository participantRepository;
    @Autowired protected MessageRepository messageRepository;
    @Autowired protected OutboxEventRepository outboxRepository;

    @MockitoBean
    protected KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * No @Transactional on this class on purpose: a wrapping transaction would
     * hide flush-ordering bugs and break concurrency tests, which need real commits.
     */
    @BeforeEach
    void cleanState() {
        messageRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        conversationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        statistics().clear();
    }

    protected Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    protected long countQueries(Runnable action) {
        Statistics stats = statistics();
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    protected User givenUser(long id, String username) {
        return userRepository.save(new User(id, username));
    }

    protected <T> T singlePayload(Class<T> type) {
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        return objectMapper.readValue(events.getFirst().getPayload(), type);
    }

    protected void assertFailsWith(HttpStatus status, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .asInstanceOf(InstanceOfAssertFactories.type(ChatServiceException.class))
                .extracting(ChatServiceException::getHttpStatus)
                .isEqualTo(status);
    }
}