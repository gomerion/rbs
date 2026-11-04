package ru.ssau.rbs.service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.entity.LoginAttempt;
import ru.ssau.rbs.domain.entity.RevokedToken;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.security.*;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.LoginAttemptRepository;
import ru.ssau.rbs.repository.RevokedTokenRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.security.LoginAttemptRecorder;

@Service 
@RequiredArgsConstructor 
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final LoginAttemptRecorder attemptRecorder;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.emailTaken(email);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.EMPLOYEE);
        try {
            User saved = userRepository.saveAndFlush(user);
            return new UserResponse(saved.getId(), saved.getEmail(), saved.getRole());
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.emailTaken(email);
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request, InetAddress ip) {
        String email = normalize(request.getEmail());
        Instant windowStart = Instant.now().minus(ATTEMPT_WINDOW);

        if (loginAttemptRepository.countByEmailAndIpAndCreatedAtAfter(email, ip, windowStart) >= MAX_FAILED_ATTEMPTS) {
            throw ApiException.rateLimited(retryAfter(email, ip));
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException ex) {
            attemptRecorder.recordFailure(email, ip);
            throw ApiException.unauthenticated("Неверный email или пароль");
        }

        loginAttemptRepository.deleteByEmailAndIp(email, ip);
        String token = jwtService.generateToken((UserDetails) authentication.getPrincipal());
        return new TokenResponse(token, "Bearer", jwtService.getExpirationSeconds(token));
    }

    @Transactional
    public void logout(String token) {
        String tokenId = jwtService.extractTokenId(token);
        if (tokenId != null && !revokedTokenRepository.existsById(tokenId)) {
            revokedTokenRepository.save(new RevokedToken(tokenId, jwtService.extractExpiration(token).toInstant()));
        }
    }

    private Duration retryAfter(String email, InetAddress ip) {
        return loginAttemptRepository.findFirstByEmailAndIpOrderByCreatedAtAsc(email, ip)
                .map(LoginAttempt::getCreatedAt)
                .map(oldest -> Duration.between(Instant.now(), oldest.plus(ATTEMPT_WINDOW)))
                .orElse(ATTEMPT_WINDOW);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

}
