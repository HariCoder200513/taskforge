package io.taskforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit record. {@code userId} is nullable (failed logins have no
 * authenticated user). The application never updates or deletes these rows.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "request_id")
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(UUID userId, String action, String resourceType, String resourceId,
                    String ip, String requestId, Map<String, Object> metadata) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ip = ip;
        this.requestId = requestId;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }
}
