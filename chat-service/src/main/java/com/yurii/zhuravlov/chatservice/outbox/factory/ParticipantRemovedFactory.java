package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.ParticipantRemovedPayload;
import com.yurii.zhuravlov.contracts.chat.ParticipantRemoved;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParticipantRemovedFactory extends AbstractChatEventFactory<ParticipantRemovedPayload> {
    public ParticipantRemovedFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected Object toAvro(ParticipantRemovedPayload payload) {
        return ParticipantRemoved.newBuilder()
                .setSelfLeave(payload.selfLeave())
                .setUserId(payload.userId())
                .build();
    }

    @Override
    public String eventType() {
        return ParticipantRemoved.class.getSimpleName();
    }

    @Override
    public Class<ParticipantRemovedPayload> payloadType() {
        return ParticipantRemovedPayload.class;
    }
}
