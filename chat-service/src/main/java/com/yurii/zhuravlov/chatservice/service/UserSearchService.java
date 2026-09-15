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
    private static final String LIKE_ESCAPE = "\\";

    @Transactional(readOnly = true)
    public List<UserResponse> search(String query, Long requesterId, int page) {
        return userRepository
                .searchExcluding(escapeLikePattern(query), requesterId, PageRequest.of(page, PAGE_SIZE))
                .stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername()))
                .toList();
    }

    /**
     * The query goes into a LIKE pattern, where % and _ are wildcards. Without escaping,
     * a search for "%" would return every user. The escape character itself must be
     * doubled first, otherwise it would escape the backslash we add afterwards.
     */
    private String escapeLikePattern(String query) {
        return query
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}