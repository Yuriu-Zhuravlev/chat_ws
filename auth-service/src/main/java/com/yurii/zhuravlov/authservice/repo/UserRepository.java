package com.yurii.zhuravlov.authservice.repo;

import com.yurii.zhuravlov.authservice.entities.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE lower(u.username) LIKE lower(concat('%', :query, '%')) " +
            "AND u.id <> :excludeId ORDER BY u.username")
    List<User> searchExcluding(@Param("query") String query,
                               @Param("excludeId") Long excludeId,
                               Pageable pageable);

    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(String username);

}
