package com.yurii.zhuravlov.chatservice.service;

import com.yurii.zhuravlov.chatservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserProjectionService {

    private final UserRepository userRepository;

    @Transactional
    public void upsert(Long userId, String username) {
        userRepository.insertIgnoringConflict(userId, username, Instant.now());
    }
}