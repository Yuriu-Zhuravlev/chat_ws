package com.yurii.zhuravlov.notificationservice.ws.frame;

import java.time.Instant;
import java.util.List;

public final class EventPayloads {

    public record MessageCreated(long messageId, long senderId, String clientMessageId,
                                 String content, Instant createdAt) {}

    public record MessagesRead(long readerId, long lastReadMessageId) {}

    public record ParticipantAdded(long userId, String username) {}

    public record ParticipantRemoved(long userId, boolean selfLeave) {}

    public record ConversationUpdated(String title, long adminId) {}

    public record ConversationDeleted() {}

    public record ConversationCreated(String title, long adminId, List<Participant> participants) {}

    public record Participant(long userId, String username) {}

    private EventPayloads() {}
}