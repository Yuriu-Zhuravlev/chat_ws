package com.yurii.zhuravlov.chatservice.config;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltTemplate(producerFactory),
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }

    /**
     * Built from the auto-configured factory's own config, so it inherits the broker address
     * that connection details resolved (@ServiceConnection, Compose, a cloud binding) —
     * KafkaProperties would still carry the YAML value.
     *
     * Serializers are chosen per object, because a dead letter is not uniformly typed:
     * a key that deserialized fine arrives as String, a payload that failed arrives as raw
     * bytes, and a record that failed in the listener after its retries arrives as Avro.
     */
    private KafkaTemplate<Object, Object> dltTemplate(ProducerFactory<?, ?> producerFactory) {
        Map<String, Object> config = new HashMap<>(producerFactory.getConfigurationProperties());
        config.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
        config.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(config, false);

        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(String.class, new StringSerializer());
        delegates.put(SpecificRecord.class, avroSerializer);

        // assignable = true: SpecificRecord must match the generated subclasses.
        Serializer<Object> serializer = new DelegatingByTypeSerializer(delegates, true);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config, serializer, serializer));
    }
}