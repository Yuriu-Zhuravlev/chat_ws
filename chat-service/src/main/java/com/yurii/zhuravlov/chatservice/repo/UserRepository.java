package com.yurii.zhuravlov.chatservice.repo;

import com.yurii.zhuravlov.chatservice.entities.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE lower(u.username) LIKE lower(concat('%', :query, '%')) " +
            "AND u.id <> :excludeId ORDER BY u.username")
    List<User> searchExcluding(@Param("query") String query,
                               @Param("excludeId") Long excludeId,
                               Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT INTO chat_schema.users (id, username, created_at)
        VALUES (:id, :username, :createdAt)
        ON CONFLICT (id) DO NOTHING
        """, nativeQuery = true)
    void insertIgnoringConflict(@Param("id") Long id,
                                @Param("username") String username,
                                @Param("createdAt") Instant createdAt);
}
