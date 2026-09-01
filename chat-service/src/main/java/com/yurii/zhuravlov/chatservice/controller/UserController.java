package com.yurii.zhuravlov.chatservice.controller;

import com.yurii.zhuravlov.chatservice.dto.request.UserSearchRequest;
import com.yurii.zhuravlov.chatservice.dto.response.UserResponse;
import com.yurii.zhuravlov.chatservice.security.CurrentUserId;
import com.yurii.zhuravlov.chatservice.service.UserSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSearchService userSearchService;

    @GetMapping("/search")
    public List<UserResponse> search(@Valid UserSearchRequest request,
                                     @CurrentUserId Long userId) {
        return userSearchService.search(request.query(), userId, request.page());
    }
}