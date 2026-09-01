package com.yurii.zhuravlov.chatservice.service;

import com.yurii.zhuravlov.chatservice.dto.response.UserResponse;
import com.yurii.zhuravlov.chatservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.yurii.zhuravlov.chatservice.constants.Constants.PAGE_SIZE;

@Service
@RequiredArgsConstructor
public class UserSearchService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> search(String query, Long requesterId, int page) {
        return userRepository
                .searchExcluding(query, requesterId, PageRequest.of(page, PAGE_SIZE))
                .stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername()))
                .toList();
    }
}