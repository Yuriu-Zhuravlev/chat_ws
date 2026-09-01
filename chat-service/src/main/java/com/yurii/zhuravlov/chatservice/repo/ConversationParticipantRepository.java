package com.yurii.zhuravlov.chatservice.repo;

import com.yurii.zhuravlov.chatservice.dto.projection.ConversationSummaryRow;
import com.yurii.zhuravlov.chatservice.dto.projection.ParticipantRow;
import com.yurii.zhuravlov.chatservice.dto.response.UserResponse;
import com.yurii.zhuravlov.chatservice.entities.ConversationParticipant;
import com.yurii.zhuravlov.chatservice.entities.ParticipantId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ParticipantId> {
    @Query("""
            SELECT new com.yurii.zhuravlov.chatservice.dto.projection.ConversationSummaryRow(
                c.id,
                c.title,
                c.adminId,
                c.lastMessageAt,
                (SELECT COUNT(m) FROM Message m
                  WHERE m.conversationId = c.id
                    AND m.senderId <> :me
                    AND (p.lastReadMessageId IS NULL OR m.id > p.lastReadMessageId)),
                (SELECT COUNT(p2) FROM ConversationParticipant p2
                  WHERE p2.id.conversationId = c.id
                    AND p2.id.userId <> :me)
            )
            FROM ConversationParticipant p
            JOIN Conversation c ON c.id = p.id.conversationId
            WHERE p.id.userId = :me
            ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
            """)
    List<ConversationSummaryRow> findSummaries(@Param("me") Long me, Pageable pageable);

    @Query("""
            SELECT new com.yurii.zhuravlov.chatservice.dto.projection.ParticipantRow(
                p.id.conversationId, u.id, u.username)
            FROM ConversationParticipant p
            JOIN User u ON u.id = p.id.userId
            WHERE p.id.conversationId IN :conversationIds
              AND p.id.userId <> :me
            ORDER BY u.username
            """)
    List<ParticipantRow> findParticipantRows(
            @Param("conversationIds") Collection<Long> conversationIds,
            @Param("me") Long me);

    @Query("""
            SELECT new com.yurii.zhuravlov.chatservice.dto.response.UserResponse(u.id, u.username)
            FROM ConversationParticipant p
            JOIN User u ON u.id = p.id.userId
            WHERE p.id.conversationId = :conversationId
            ORDER BY u.username
            """)
    List<UserResponse> findParticipants(@Param("conversationId") Long conversationId);


    @Query("SELECT p.id.userId FROM ConversationParticipant p WHERE p.id.conversationId = :id")
    List<Long> findUserIds(@Param("id") Long conversationId);

    long countById_ConversationId(Long conversationId);

    @Modifying
    @Query(value = """
        INSERT INTO chat_schema.conversation_participants
            (conversation_id, user_id, joined_at)
        VALUES (:conversationId, :userId, now())
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("conversationId") Long conversationId,
                       @Param("userId") Long userId);
}