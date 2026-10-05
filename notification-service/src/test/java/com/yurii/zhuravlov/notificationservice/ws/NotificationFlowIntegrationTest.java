package com.yurii.zhuravlov.notificationservice.ws;

import com.yurii.zhuravlov.contracts.chat.ChatEvent;
import com.yurii.zhuravlov.contracts.chat.ConversationCreated;
import com.yurii.zhuravlov.contracts.chat.MessageCreated;
import com.yurii.zhuravlov.contracts.chat.ParticipantInfo;
import com.yurii.zhuravlov.notificationservice.IntegrationTestBase;
import com.yurii.zhuravlov.notificationservice.TestJwtConfiguration;
import com.yurii.zhuravlov.notificationservice.TestTokens;
import com.yurii.zhuravlov.notificationservice.config.properties.KafkaTopicProperties;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestJwtConfiguration.class)
class NotificationFlowIntegrationTest extends IntegrationTestBase {

    @LocalServerPort int port;

    @Autowired
    TestTokens tokens;
    @Autowired
    KafkaTopicProperties topics;
    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;
    @Autowired
    KafkaContainer kafka;

    @Value("${spring.kafka.consumer.properties.schema.registry.url}") String registryUrl;

    private KafkaProducer<String, Object> producer;

    /**
     * auto-offset-reset is "latest", so an event published before the consumer owns its
     * partitions is simply never seen. Without this wait the delivery tests fail at random.
     */
    @BeforeEach
    void waitForPartitionAssignment() {
        listenerRegistry.getListenerContainers()
                .forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));

        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                "schema.registry.url", registryUrl,
                "auto.register.schemas", true));
    }

    @AfterEach
    void closeProducer() {
        producer.close();
    }

    @Nested
    class Authentication {

        @Test
        void closesConnectionThatNeverAuthenticates() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                CloseStatus status = client.awaitClose(Duration.ofSeconds(5));

                assertThat(status.getCode()).isEqualTo(4401);
            }
        }

        @Test
        void acceptsValidToken() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.send("{\"type\":\"AUTH\",\"token\":\"%s\"}".formatted(tokens.valid(1L)));

                assertThat(client.nextFrame()).isEqualTo("{\"type\":\"READY\",\"userId\":1}");
            }
        }

        @Test
        void rejectsGarbageToken() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.send("{\"type\":\"AUTH\",\"token\":\"not-a-jwt\"}");

                assertThat(client.awaitClose(Duration.ofSeconds(5)).getCode()).isEqualTo(4401);
            }
        }

        /** Answering anything before AUTH would confirm the endpoint to an unauthenticated peer. */
        @Test
        void closesOnAnyFrameBeforeAuth() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.send("{\"type\":\"PING\"}");

                assertThat(client.awaitClose(Duration.ofSeconds(5)).getCode()).isEqualTo(4401);
            }
        }

        @Test
        void answersPingAfterAuth() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.authenticate(tokens.valid(1L));

                client.send("{\"type\":\"PING\"}");

                assertThat(client.nextFrame()).isEqualTo("{\"type\":\"PONG\"}");
            }
        }

        /**
         * The 60s default clock skew lets a token expiring in two seconds decode fine, so
         * what closes the socket here is the scheduled task, not the validator.
         */
        @Test
        void closesWhenTheTokenExpires() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.authenticate(tokens.expiringIn(1L, Duration.ofSeconds(2)));

                assertThat(client.awaitClose(Duration.ofSeconds(10)).getCode()).isEqualTo(4402);
            }
        }
    }

    @Nested
    class Delivery {

        @Test
        void deliversEventToItsRecipient() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.authenticate(tokens.valid(1L));

                publish(event(List.of(1L, 2L), messageCreated()));

                assertThat(client.nextFrame())
                        .contains("\"type\":\"EVENT\"")
                        .contains("\"type\":\"MessageCreated\"")
                        .contains("\"content\":\"hello\"")
                        .contains("\"conversationId\":4");
            }
        }

        /** recipients is the whole addressing mechanism: nothing else filters delivery. */
        @Test
        void ignoresEventAddressedToSomeoneElse() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.authenticate(tokens.valid(1L));

                publish(event(List.of(999L), messageCreated()));

                assertThat(client.nextFrameWithin(Duration.ofSeconds(2))).isNull();
            }
        }

        /** Several tabs of one person each get their own copy; dedup is the client's job. */
        @Test
        void deliversToEverySessionOfTheSameUser() throws Exception {
            try (RecordingWebSocketClient first = new RecordingWebSocketClient(port);
                 RecordingWebSocketClient second = new RecordingWebSocketClient(port)) {

                first.authenticate(tokens.valid(1L));
                second.authenticate(tokens.valid(1L));

                publish(event(List.of(1L), messageCreated()));

                assertThat(first.nextFrame()).contains("MessageCreated");
                assertThat(second.nextFrame()).contains("MessageCreated");
            }
        }

        @Test
        void mapsConversationCreatedWithRoster() throws Exception {
            try (RecordingWebSocketClient client = new RecordingWebSocketClient(port)) {
                client.authenticate(tokens.valid(1L));

                publish(event(List.of(1L), ConversationCreated.newBuilder()
                        .setTitle("chat")
                        .setAdminId(1L)
                        .setParticipants(List.of(ParticipantInfo.newBuilder()
                                .setUserId(1L).setUsername("alice").build()))
                        .build()));

                assertThat(client.nextFrame())
                        .contains("\"type\":\"ConversationCreated\"")
                        .contains("\"username\":\"alice\"")
                        .doesNotContain("schema");   // the Avro object must not leak into the frame
            }
        }
    }

    private void publish(ChatEvent event) throws Exception {
        producer.send(new ProducerRecord<>(
                topics.chatEvents(), String.valueOf(event.getConversationId()), event)).get();
    }

    private static MessageCreated messageCreated() {
        return MessageCreated.newBuilder()
                .setMessageId(7L)
                .setSenderId(2L)
                .setClientMessageId("c1")
                .setContent("hello")
                .setCreatedAt(Instant.now())
                .build();
    }

    private static ChatEvent event(List<Long> recipients, Object payload) {
        return ChatEvent.newBuilder()
                .setEventId(1L)
                .setConversationId(4L)
                .setOccurredAt(Instant.now())
                .setRecipients(recipients)
                .setPayload(payload)
                .build();
    }
}