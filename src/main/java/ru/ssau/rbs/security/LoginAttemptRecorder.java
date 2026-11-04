package ru.ssau.rbs.security;

import java.net.InetAddress;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.entity.LoginAttempt;
import ru.ssau.rbs.repository.LoginAttemptRepository;

@Component 
@RequiredArgsConstructor 
public class LoginAttemptRecorder {

    private final LoginAttemptRepository loginAttemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, InetAddress ip) {
        LoginAttempt loginAttempt = new LoginAttempt(email, ip);
        loginAttemptRepository.save(loginAttempt);
    }

}
