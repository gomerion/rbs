package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.util.Map;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "action", nullable = false, columnDefinition = "text")
    private String action;

    @Column(name = "object_type", nullable = false, columnDefinition = "text")
    private String objectType;

    @Column(name = "object_id", nullable = false, columnDefinition = "text")
    private String objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @Column(name = "request_id", nullable = false, columnDefinition = "text")
    private String requestId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;
}
