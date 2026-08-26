package com.yurii.zhuravlov.authservice.repo;

import com.yurii.zhuravlov.authservice.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(String username);

}
