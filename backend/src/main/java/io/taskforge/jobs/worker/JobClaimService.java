package io.taskforge.jobs.worker;

import io.taskforge.domain.Job;
import io.taskforge.domain.JobStatus;
import io.taskforge.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional boundaries of the worker engine. Kept in its own bean so the
 * {@code @Transactional} proxy actually applies — if {@link WorkerEngine} called
 * these as its own methods, Spring's self-invocation would bypass the advice and
 * each step would run without a transaction (and SKIP LOCKED row locks would be
 * released immediately, defeating the claim).
 */
@Service
public class JobClaimService {

    private final JobRepository jobs;
    private final WorkerProperties props;

    public JobClaimService(JobRepository jobs, WorkerProperties props) {
        this.jobs = jobs;
        this.props = props;
    }

    /**
     * Reserve up to {@code batch} due jobs for {@code workerId}. The native
     * SELECT ... FOR UPDATE SKIP LOCKED locks a disjoint set of rows; moving them
     * to RUNNING in the same transaction holds those locks until commit, so no
     * other worker can take them. Returns the claimed job ids.
     */
    @Transactional
    public List<UUID> claim(String workerId, int batch) {
        List<UUID> ids = jobs.lockClaimableIds(batch);
        if (ids.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        jobs.findAllById(ids).forEach(j -> j.claim(workerId, now));
        return ids; // flushed by dirty checking on commit
    }

    @Transactional(readOnly = true)
    public Optional<Job> find(UUID id) {
        return jobs.findById(id);
    }

    @Transactional
    public void succeed(UUID id, Map<String, Object> result) {
        jobs.findById(id).ifPresent(j -> j.succeed(result, Instant.now()));
    }

    /** Failure: reschedule with backoff, or dead-letter once attempts are spent. */
    @Transactional
    public void fail(UUID id, String error) {
        jobs.findById(id).ifPresent(j -> {
            Instant now = Instant.now();
            if (j.attemptsExhausted()) {
                j.markDead(error, now);
            } else {
                j.reschedule(error, now.plus(props.backoffFor(j.getAttempts())), now);
            }
        });
    }

    /** Requeue RUNNING jobs whose worker went silent past the lease timeout. */
    @Transactional
    public int reapStale() {
        Instant now = Instant.now();
        List<Job> stale = jobs.findStaleRunning(JobStatus.RUNNING, now.minus(props.leaseTimeout()));
        stale.forEach(j -> j.requeueAfterLeaseExpiry(now));
        return stale.size();
    }
}
