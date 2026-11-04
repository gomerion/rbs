package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt extends CreatedAtEntity {

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", nullable = false, columnDefinition = "inet")
    private InetAddress ip;
}
