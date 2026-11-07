package io.taskforge.jobs;

import io.taskforge.auth.error.AuthExceptions.ResourceNotFoundException;
import io.taskforge.domain.Job;
import io.taskforge.domain.Queue;
import io.taskforge.domain.QueuePermission;
import io.taskforge.domain.QueuePermissionType;
import io.taskforge.repository.JobRepository;
import io.taskforge.repository.QueuePermissionRepository;
import io.taskforge.repository.QueueRepository;
import io.taskforge.security.CurrentUser;
import io.taskforge.web.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Queue and job operations with resource-level authorization. Role gates live on
 * the controller (@PreAuthorize); this enforces ownership/permission and the
 * 404-not-403 visibility rule (ARCHITECTURE §4.2, §5.3).
 */
@Service
public class JobQueueService {

    private final QueueRepository queues;
    private final JobRepository jobs;
    private final QueuePermissionRepository permissions;
    private final CurrentUser currentUser;

    public JobQueueService(QueueRepository queues, JobRepository jobs,
                           QueuePermissionRepository permissions, CurrentUser currentUser) {
        this.queues = queues;
        this.jobs = jobs;
        this.permissions = permissions;
        this.currentUser = currentUser;
    }

    // ---- Queues -------------------------------------------------------------

    @Transactional
    public Queue createQueue(String name) {
        if (queues.existsByName(name)) {
            throw new ConflictException("A queue with that name already exists");
        }
        return queues.save(new Queue(name, currentUser.id()));
    }

    @Transactional(readOnly = true)
    public List<Queue> listQueues() {
        return currentUser.isAdmin() ? queues.findAll() : queues.findVisibleTo(currentUser.id());
    }

    @Transactional(readOnly = true)
    public Queue getQueue(UUID id) {
        Queue q = queues.findById(id).orElseThrow(() -> notFound("Queue"));
        if (!canViewQueue(q)) {
            throw notFound("Queue");
        }
        return q;
    }

    @Transactional
    public void grantPermission(UUID queueId, UUID targetUserId, QueuePermissionType type) {
        // Admin-gated at controller; ensure the queue exists.
        queues.findById(queueId).orElseThrow(() -> notFound("Queue"));
        permissions.save(new QueuePermission(queueId, targetUserId, type));
    }

    // ---- Jobs ---------------------------------------------------------------

    @Transactional
    public Job submitJob(UUID queueId, Map<String, Object> payload) {
        // Must be able to see the queue at all, else 404 (no enumeration).
        Queue q = queues.findById(queueId).orElseThrow(() -> notFound("Queue"));
        if (!canViewQueue(q)) {
            throw notFound("Queue");
        }
        // Visible, but submitting needs SUBMIT (or admin/queue owner) → else 403.
        if (!canSubmit(q)) {
            throw new AccessDeniedException("You do not have SUBMIT permission on this queue");
        }
        return jobs.save(new Job(queueId, currentUser.id(), payload));
    }

    @Transactional(readOnly = true)
    public List<Job> listJobs() {
        return currentUser.isAdmin() ? jobs.findAll() : jobs.findVisibleTo(currentUser.id());
    }

    @Transactional(readOnly = true)
    public Job getJob(UUID id) {
        Job j = jobs.findById(id).orElseThrow(() -> notFound("Job"));
        if (!canViewJob(j)) {
            throw notFound("Job");
        }
        return j;
    }

    @Transactional
    public Job retryJob(UUID id) {
        Job j = jobs.findById(id).orElseThrow(() -> notFound("Job"));
        if (!canViewJob(j)) {
            throw notFound("Job");
        }
        // Retry requires explicit RETRY on the job's queue, or ADMIN (decision §9.2).
        if (!currentUser.isAdmin()
                && !permissions.hasAnyPermission(j.getQueueId(), currentUser.id(),
                        List.of(QueuePermissionType.RETRY.name()))) {
            throw new AccessDeniedException("You do not have RETRY permission on this queue");
        }
        j.manualRetry(java.time.Instant.now());
        return jobs.save(j);
    }

    // ---- authorization helpers ---------------------------------------------

    private boolean canViewQueue(Queue q) {
        UUID uid = currentUser.id();
        return currentUser.isAdmin()
                || q.getOwnerId().equals(uid)
                || permissions.hasAnyPermissionAtAll(q.getId(), uid);
    }

    private boolean canSubmit(Queue q) {
        UUID uid = currentUser.id();
        return currentUser.isAdmin()
                || q.getOwnerId().equals(uid)
                || permissions.hasAnyPermission(q.getId(), uid, List.of(QueuePermissionType.SUBMIT.name()));
    }

    private boolean canViewJob(Job j) {
        UUID uid = currentUser.id();
        if (currentUser.isAdmin() || j.getOwnerId().equals(uid)) {
            return true;
        }
        return queues.findById(j.getQueueId()).map(this::canViewQueue).orElse(false);
    }

    private ResourceNotFoundException notFound(String what) {
        return new ResourceNotFoundException(what + " not found");
    }
}
