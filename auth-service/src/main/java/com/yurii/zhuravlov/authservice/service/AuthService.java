package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.dto.requests.LoginRequest;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshGraceCache refreshGraceCache;

    private static final String DUMMY_HASH =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";


    public TokenPair login(LoginRequest request) {
        Optional<User> found = userRepository.findByUsernameIgnoreCase(request.username());

        String hash = found.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid username or password");
        }

        User user = found.get();

        return sessionService.startNewSession(user);
    }

    public TokenPair refresh(String token){
        String tokenHash = TokenIssuer.sha256Hex(token);
        Optional<TokenPair> graceToken = refreshGraceCache.find(tokenHash);
        if (graceToken.isPresent()){
            return graceToken.get();
        }
        TokenPair result = sessionService.refreshSession(tokenHash);
        refreshGraceCache.put(tokenHash, result);
        return result;
    }

    public void logout(String token){
        String tokenHash = TokenIssuer.sha256Hex(token);
        sessionService.sessionLogout(tokenHash);
    }
}
