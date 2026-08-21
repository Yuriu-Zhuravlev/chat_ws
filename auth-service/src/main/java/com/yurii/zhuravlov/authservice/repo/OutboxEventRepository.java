package com.yurii.zhuravlov.authservice.repo;

import com.yurii.zhuravlov.authservice.entities.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL " +
           "AND e.attempts < :maxAttempts ORDER BY e.id")
    List<OutboxEvent> findUnpublished(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt < :threshold")
    int deletePublishedBefore(@Param("threshold") Instant threshold);

    @Query(value = """
    SELECT * FROM auth_schema.outbox_events
    WHERE published_at IS NULL AND attempts < :maxAttempts
    ORDER BY id LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("maxAttempts") int maxAttempts,
                                               @Param("batchSize") int batchSize);
}