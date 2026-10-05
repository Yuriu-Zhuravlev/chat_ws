package com.yurii.zhuravlov.notificationservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;

import java.util.function.Supplier;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** JVM image: kafka-native fails to start here because of advertised.listeners. */
    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer("apache/kafka:4.0.0");
    }

    /** In-memory storage by default in 3.x: a fresh registry per test run. */
    @Bean
    GenericContainer<?> apicurioContainer() {
        return new GenericContainer<>("apicurio/apicurio-registry:3.0.6")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/health/ready").forPort(8080));
    }

    /**
     * Supplier<Object>, not Supplier<String>: generics are invariant, and
     * DynamicPropertyRegistry.add takes the former.
     */
    @Bean
    DynamicPropertyRegistrar schemaRegistryProperties(GenericContainer<?> apicurioContainer) {
        return registry -> {
            Supplier<Object> url = () -> "http://%s:%d/apis/ccompat/v7".formatted(
                    apicurioContainer.getHost(), apicurioContainer.getMappedPort(8080));
            registry.add("spring.kafka.producer.properties.schema.registry.url", url);
            registry.add("spring.kafka.consumer.properties.schema.registry.url", url);
        };
    }
}
