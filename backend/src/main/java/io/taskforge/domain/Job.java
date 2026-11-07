package io.taskforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A unit of work. Submission creates it PENDING and immediately due; the worker
 * engine claims it (→ RUNNING), then drives it to a terminal state:
 * SUCCEEDED, or FAILED-then-rescheduled, or DEAD once attempts are exhausted.
 *
 * All lifecycle transitions are expressed as methods here so the rules live with
 * the data; the engine/service never mutate these fields directly.
 */
@Entity
@Table(name = "jobs")
public class Job {

    /** Last-error text is capped so a huge stack trace can't bloat the row. */
    private static final int MAX_ERROR_LEN = 4000;

    @Id
    private UUID id;

    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    /** The job is claimable only when PENDING and availableAt <= now (backoff). */
    @Column(name = "available_at", nullable = false)
    private Instant availableAt = Instant.now();

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_error")
    private String lastError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Job() {
    }

    public Job(UUID queueId, UUID ownerId, Map<String, Object> payload) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.queueId = queueId;
        this.ownerId = ownerId;
        this.payload = payload;
        this.status = JobStatus.PENDING;
        this.availableAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ---- lifecycle ----------------------------------------------------------

    /** Worker takes the job: RUNNING, attempt consumed, lease stamped. */
    public void claim(String workerId, Instant now) {
        this.status = JobStatus.RUNNING;
        this.attempts += 1;
        this.lockedBy = workerId;
        this.lockedAt = now;
        this.heartbeatAt = now;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.updatedAt = now;
    }

    /** Extend the lease while a long job is still running. */
    public void heartbeat(Instant now) {
        this.heartbeatAt = now;
        this.updatedAt = now;
    }

    public void succeed(Map<String, Object> result, Instant now) {
        this.status = JobStatus.SUCCEEDED;
        this.result = result;
        this.lastError = null;
        this.finishedAt = now;
        clearLease();
        this.updatedAt = now;
    }

    /** Transient failure: reschedule for a future attempt (backoff). */
    public void reschedule(String error, Instant availableAt, Instant now) {
        this.status = JobStatus.PENDING;
        this.lastError = truncate(error);
        this.availableAt = availableAt;
        this.finishedAt = null;
        clearLease();
        this.updatedAt = now;
    }

    /** Terminal failure: no attempts left (or lost beyond recovery). */
    public void markDead(String error, Instant now) {
        this.status = JobStatus.DEAD;
        this.lastError = truncate(error);
        this.finishedAt = now;
        clearLease();
        this.updatedAt = now;
    }

    /** Reaper recovery for a crashed worker: requeue now, or bury if exhausted. */
    public void requeueAfterLeaseExpiry(Instant now) {
        if (this.attempts >= this.maxAttempts) {
            markDead("lease expired (worker lost) after " + attempts + " attempt(s)", now);
            return;
        }
        this.status = JobStatus.PENDING;
        this.lastError = "lease expired (worker lost); requeued";
        this.availableAt = now;
        this.finishedAt = null;
        clearLease();
        this.updatedAt = now;
    }

    /** Operator-initiated retry: fresh budget, due immediately. */
    public void manualRetry(Instant now) {
        this.status = JobStatus.PENDING;
        this.attempts = 0;
        this.availableAt = now;
        this.lastError = null;
        this.result = null;
        this.finishedAt = null;
        clearLease();
        this.updatedAt = now;
    }

    public boolean attemptsExhausted() {
        return this.attempts >= this.maxAttempts;
    }

    private void clearLease() {
        this.lockedBy = null;
        this.lockedAt = null;
        this.heartbeatAt = null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }

    // ---- getters ------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getQueueId() {
        return queueId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
