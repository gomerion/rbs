package ru.ssau.rbs.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.ssau.rbs.domain.entity.LoginAttempt;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByEmailAndIpAndCreatedAtAfter(String email, InetAddress ip, Instant after);

    List<LoginAttempt> findAllByEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    @Transactional
    @Modifying
    @Query("delete from LoginAttempt l where l.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);

    @Transactional
    long deleteByEmailAndIp(String email, InetAddress ip);

    Optional<LoginAttempt> findFirstByEmailAndIpOrderByCreatedAtAsc(String email, InetAddress ip);

}
