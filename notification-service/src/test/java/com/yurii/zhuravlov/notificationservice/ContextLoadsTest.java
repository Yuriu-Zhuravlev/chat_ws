package com.yurii.zhuravlov.notificationservice;

import com.yurii.zhuravlov.notificationservice.config.properties.KafkaTopicProperties;
import com.yurii.zhuravlov.notificationservice.config.properties.WebSocketProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ContextLoadsTest extends IntegrationTestBase {

    @Autowired
    KafkaTopicProperties topics;

    @Autowired
    WebSocketProperties webSocket;

    /**
     * Also proves @ConfigurationPropertiesScan is in place: without it these two would
     * not be beans at all, and the failure would only surface once something injected them.
     */
    @Test
    void bindsConfigurationProperties() {
        assertThat(topics.chatEvents()).isEqualTo("chat-events");
        assertThat(webSocket.path()).isEqualTo("/ws");
        assertThat(webSocket.authTimeout()).isNotNull();
    }
}
