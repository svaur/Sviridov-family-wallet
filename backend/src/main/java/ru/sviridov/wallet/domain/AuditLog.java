package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One immutable entry in the admin panel's audit trail.
 *
 * <p>{@code userId} is left {@code null} for admin entries — the durable target
 * reference is {@code entityId} (it survives a user delete, while the foreign
 * key would be SET NULL). The target username is snapshotted into
 * {@code newValue} so the trail stays readable after a user is removed.</p>
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 40)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 20)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValue;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "actor_kind", nullable = false, length = 16)
    private String actorKind;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(length = 45)
    private String ip;

    protected AuditLog() {
    }

    public AuditLog(Long userId, String entity, Long entityId, String action,
                    String oldValue, String newValue, String actorKind, Long actorId, String ip) {
        this.userId = userId;
        this.entity = entity;
        this.entityId = entityId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.at = Instant.now();
        this.actorKind = actorKind;
        this.actorId = actorId;
        this.ip = ip;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEntity() {
        return entity;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getAt() {
        return at;
    }

    public String getActorKind() {
        return actorKind;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getIp() {
        return ip;
    }
}
