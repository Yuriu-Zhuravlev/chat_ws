package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.exceptions.AuthServiceException;
import com.yurii.zhuravlov.authservice.exceptions.UserAlreadyExists;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRegistrar userRegistrar;


    public void register(RegistrationRequest request) {
        if (userRepository.findByUsernameIgnoreCase(request.username()).isPresent()) {
            throw new UserAlreadyExists("Username is already taken");
        }
        String hash = passwordEncoder.encode(request.password());
        try {
            userRegistrar.createWithEvent(request.username(), hash);
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


}
