package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.UserAlreadyExists;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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


}
