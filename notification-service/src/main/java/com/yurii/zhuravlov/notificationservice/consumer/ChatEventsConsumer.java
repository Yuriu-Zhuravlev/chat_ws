package com.yurii.zhuravlov.notificationservice.consumer;

import com.yurii.zhuravlov.contracts.chat.ChatEvent;
import com.yurii.zhuravlov.notificationservice.ws.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatEventsConsumer {

    private final EventDispatcher dispatcher;

    @KafkaListener(
            topics = "${app.kafka.topics.chat-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onChatEvent(ChatEvent event) {
        log.debug("Received {} for conversation {}", event.getPayload().getClass().getSimpleName(),
                event.getConversationId());
        dispatcher.dispatch(event);
    }
}