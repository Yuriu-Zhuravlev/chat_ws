package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.ParticipantAddedPayload;
import com.yurii.zhuravlov.contracts.chat.ParticipantAdded;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParticipantAddedFactory extends AbstractChatEventFactory<ParticipantAddedPayload>{
    public ParticipantAddedFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected Object toAvro(ParticipantAddedPayload payload) {
        return ParticipantAdded.newBuilder()
                .setUserId(payload.userId())
                .setUsername(payload.username())
                .build();
    }

    @Override
    public String eventType() {
        return ParticipantAdded.class.getSimpleName();
    }

    @Override
    public Class<ParticipantAddedPayload> payloadType() {
        return ParticipantAddedPayload.class;
    }
}
