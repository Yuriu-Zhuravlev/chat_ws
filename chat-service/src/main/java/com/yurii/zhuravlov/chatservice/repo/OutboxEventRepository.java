package com.yurii.zhuravlov.chatservice.repo;

import com.yurii.zhuravlov.chatservice.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt < :threshold")
    int deletePublishedBefore(@Param("threshold") Instant threshold);

    @Query(value = """
    SELECT * FROM chat_schema.outbox_events
    WHERE published_at IS NULL AND attempts < :maxAttempts
    ORDER BY id LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("maxAttempts") int maxAttempts,
                                               @Param("batchSize") int batchSize);
}