package io.taskforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per issued refresh token. The raw token is never stored — only its
 * SHA-256 hash. Rotation lineage: {@code familyId} groups a login session;
 * {@code usedAt}/{@code replacedBy} mark a token as rotated so reuse can be
 * detected and the whole family revoked.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip")
    private String ip;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, UUID familyId,
                        Instant expiresAt, Instant absoluteExpiresAt,
                        String userAgent, String ip) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.userAgent = userAgent;
        this.ip = ip;
        this.createdAt = Instant.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    public void markUsed(Instant when, UUID replacedBy) {
        this.usedAt = when;
        this.replacedBy = replacedBy;
    }

    public void revoke(Instant when) {
        if (this.revokedAt == null) {
            this.revokedAt = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
