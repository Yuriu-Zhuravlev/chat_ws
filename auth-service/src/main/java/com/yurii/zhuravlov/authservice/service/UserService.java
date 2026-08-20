package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.AuthServiceException;
import com.yurii.zhuravlov.authservice.exceptions.UserAlreadyExists;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public void register(RegistrationRequest request) {
        if (userRepository.findByUsernameIgnoreCase(request.username()).isPresent()) {
            throw new UserAlreadyExists("Username is already taken");
        }
        User user = new User(passwordEncoder.encode(request.password()), request.username());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExists("Username is already taken");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(u -> new UserResponse(u.getId(), u.getUsername()))
                .orElseThrow(() -> new AuthServiceException("User not found", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> search(String query, Long excludeUserId, int page) {
        return userRepository
                .searchExcluding(query, excludeUserId, PageRequest.of(page, 20))
                .stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername()))
                .toList();
    }
}
