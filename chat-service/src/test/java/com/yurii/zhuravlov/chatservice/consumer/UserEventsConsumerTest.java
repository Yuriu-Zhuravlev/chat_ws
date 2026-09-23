package com.yurii.zhuravlov.chatservice.consumer;


import com.yurii.zhuravlov.chatservice.IntegrationTestBase;
import com.yurii.zhuravlov.chatservice.config.properties.KafkaTopicProperties;
import com.yurii.zhuravlov.chatservice.entities.User;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class UserEventsConsumerTest extends IntegrationTestBase {

    @Autowired
    KafkaContainer kafka;
    @Autowired
    KafkaTopicProperties topics;

    @Value("${spring.kafka.consumer.properties.schema.registry.url}")
    String registryUrl;

    private static Schema writerSchema;
    private KafkaProducer<String, Object> avroProducer;

    @BeforeAll
    static void loadWriterSchema() throws IOException {
        try (InputStream in = UserEventsConsumerTest.class
                .getResourceAsStream("/avro-writer/UserRegistered.avsc")) {
            writerSchema = new Schema.Parser().parse(in);
        }
    }

    @BeforeEach
    void producer() {
        avroProducer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                "schema.registry.url", registryUrl,
                "auto.register.schemas", true));
    }

    @AfterEach
    void closeProducer() {
        avroProducer.close();
    }

    /**
     * The event is written with auth's wider schema (with occurredAt) and read into
     * chat's narrower reader class. Avro schema resolution drops the extra field.
     */
    @Test
    void readsEventWrittenWithWiderSchema() throws Exception {
        send(userRegistered(1001L, "vasyl"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(userRepository.findById(1001L))
                        .get().extracting(User::getUsername).isEqualTo("vasyl"));
    }

    /** At-least-once delivery means redelivery is normal; the upsert must absorb it. */
    @Test
    void duplicateEventIsAbsorbed() throws Exception {
        GenericRecord event = userRegistered(1002L, "petro");
        send(event);
        send(event);
        send(userRegistered(1003L, "marker"));   // processed after both duplicates

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(userRepository.existsById(1003L)).isTrue());

        assertThat(userRepository.findAll())
                .filteredOn(u -> u.getId() == 1002L).hasSize(1);
    }

    /**
     * A malformed record must not block the partition and must not vanish: it lands in
     * the DLT with headers explaining why. The topic is shared by the whole run, so the
     * assertion filters by this test's own payload instead of counting everything.
     */
    @Test
    void poisonPillGoesToDeadLetterTopic() throws Exception {
        byte[] poison = ("not avro " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);

        sendRaw(topics.userEvents(), "1006", poison);
        send(userRegistered(1007L, "after-poison"));

        // The partition moved on: the valid event behind the poison pill was processed.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(userRepository.existsById(1007L)).isTrue());

        try (KafkaConsumer<byte[], byte[]> dlt = dltConsumer()) {
            dlt.subscribe(List.of(topics.userEvents() + ".DLT"));

            List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                dlt.poll(Duration.ofSeconds(1)).forEach(records::add);
                assertThat(records).anySatisfy(r -> assertThat(r.value()).isEqualTo(poison));
            });

            assertThat(records)
                    .filteredOn(r -> Arrays.equals(r.value(), poison))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                                .isEqualTo(topics.userEvents());
                        assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                                .contains("deserialize");
                    });
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private KafkaConsumer<byte[], byte[]> dltConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
    }

    private void sendRaw(String topic, String key, byte[] value) throws Exception {
        try (KafkaProducer<String, byte[]> raw = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            raw.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    private GenericRecord userRegistered(long userId, String username) {
        GenericRecord record = new GenericData.Record(writerSchema);
        record.put("userId", userId);
        record.put("username", username);
        record.put("occurredAt", Instant.now().toEpochMilli());
        return record;
    }

    private void send(GenericRecord event) throws Exception {
        avroProducer.send(new ProducerRecord<>(
                topics.userEvents(), event.get("userId").toString(), event)).get();
    }
}