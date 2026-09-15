package com.yurii.zhuravlov.chatservice.repo;

import com.yurii.zhuravlov.chatservice.entities.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Idempotent insert. Empty result means the client retried with the same
     * clientMessageId; the unique index resolves the race without an exception,
     * which matters because catching one inside the transaction would doom it.
     */
    @Query(value = """
            INSERT INTO chat_schema.messages
                (conversation_id, sender_id, client_message_id, content, created_at)
            VALUES (:conversationId, :senderId, :clientMessageId, :content, :createdAt)
            ON CONFLICT (conversation_id, client_message_id) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    Optional<Long> insertIfAbsent(@Param("conversationId") Long conversationId,
                                  @Param("senderId") Long senderId,
                                  @Param("clientMessageId") String clientMessageId,
                                  @Param("content") String content,
                                  @Param("createdAt") Instant createdAt);

    Optional<Message> findByConversationIdAndClientMessageId(Long conversationId,
                                                             String clientMessageId);

    /**
     * History page, newest first. Callers pass Long.MAX_VALUE for the first page:
     * the plan is the same range scan, so a separate "latest" query would earn nothing.
     */
    @Query("""
        SELECT m FROM Message m
        WHERE m.conversationId = :conversationId AND m.id < :before
        ORDER BY m.id DESC
        """)
    List<Message> findBefore(@Param("conversationId") Long conversationId,
                             @Param("before") Long before,
                             Pageable pageable);

    /** Catch-up after reconnect, oldest first. */
    @Query("""
        SELECT m FROM Message m
        WHERE m.conversationId = :conversationId AND m.id > :after
        ORDER BY m.id ASC
        """)
    List<Message> findAfter(@Param("conversationId") Long conversationId,
                            @Param("after") Long after,
                            Pageable pageable);

    boolean existsByIdAndConversationId(Long id, Long conversationId);
}