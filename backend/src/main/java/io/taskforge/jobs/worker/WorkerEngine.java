package io.taskforge.jobs.worker;

import io.taskforge.domain.Job;
import io.taskforge.domain.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates one cycle of work: claim due jobs, run each handler, record the
 * outcome. Transaction boundaries live in {@link JobClaimService}; handler
 * execution runs OUTSIDE any transaction so a slow job never holds a DB lock —
 * the lease (locked_at/heartbeat_at) plus the reaper cover crash recovery.
 */
@Service
public class WorkerEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkerEngine.class);

    private final JobClaimService claims;
    private final JobHandler handler;
    private final WorkerProperties props;
    private final String workerId;

    public WorkerEngine(JobClaimService claims, JobHandler handler, WorkerProperties props) {
        this.claims = claims;
        this.handler = handler;
        this.props = props;
        this.workerId = buildWorkerId();
    }

    /** Claim a batch and execute it. Returns how many jobs were processed. */
    public int pollOnce() {
        List<UUID> ids = claims.claim(workerId, props.batchSize());
        for (UUID id : ids) {
            execute(id);
        }
        return ids.size();
    }

    /** Requeue jobs abandoned by dead workers. Returns how many were recovered. */
    public int reapStale() {
        int n = claims.reapStale();
        if (n > 0) {
            log.warn("Reaped {} stale job(s) past lease timeout {}", n, props.leaseTimeout());
        }
        return n;
    }

    /** Claim only (no execution) — used by concurrency tests. */
    public List<UUID> claimBatch(int batch) {
        return claims.claim(workerId, batch);
    }

    public String workerId() {
        return workerId;
    }

    private void execute(UUID id) {
        Job job = claims.find(id).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) {
            return; // already moved on (e.g. reaped) — nothing to do
        }
        try {
            Map<String, Object> result = handler.handle(job);
            claims.succeed(id, result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            log.info("Job {} failed on attempt {}: {}", id, job.getAttempts(), msg);
            claims.fail(id, msg);
        }
    }

    private static String buildWorkerId() {
        String pid = ManagementFactory.getRuntimeMXBean().getName(); // pid@host
        return pid + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
