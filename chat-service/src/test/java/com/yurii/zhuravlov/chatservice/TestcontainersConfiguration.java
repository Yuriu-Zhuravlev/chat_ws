package com.yurii.zhuravlov.chatservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Supplier;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

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
