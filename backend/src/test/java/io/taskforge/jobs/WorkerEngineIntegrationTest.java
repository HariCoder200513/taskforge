package io.taskforge.jobs;

import io.taskforge.domain.Job;
import io.taskforge.domain.JobStatus;
import io.taskforge.jobs.worker.WorkerEngine;
import io.taskforge.repository.JobRepository;
import io.taskforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worker-engine lifecycle: claim → run → succeed/retry/dead-letter, the stale-
 * lease reaper, and FOR UPDATE SKIP LOCKED concurrency. The in-process scheduler
 * is off (worker.enabled=false, from application-test.yml) so the engine is
 * driven explicitly and assertions are deterministic. Zero backoff + a 1s lease
 * timeout (also from the test profile) keep retries immediately due. Requires Docker.
 */
@TestPropertySource(properties = {
        "worker.retry-base-backoff=0s",
        "worker.retry-max-backoff=0s",
        "worker.lease-timeout=1s"
})
class WorkerEngineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WorkerEngine engine;
    @Autowired
    private JobRepository jobs;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID ownerId;
    private UUID queueId;

    @BeforeEach
    void seedOwnerAndQueue() {
        ownerId = UUID.randomUUID();
        queueId = UUID.randomUUID();
        String tag = Long.toString(System.nanoTime());
        jdbc.update("insert into users (id, email, password_hash, name) values (?, ?, 'x', 'worker-test')",
                ownerId, "worker-" + tag + "@example.com");
        jdbc.update("insert into queues (id, name, owner_id) values (?, ?, ?)",
                queueId, "wq-" + tag, ownerId);
    }

    @Test
    void successfulJobRunsOnceAndRecordsResult() {
        UUID id = insertPending("{\"echo\":\"hi\"}", 3);

        assertThat(engine.pollOnce()).isEqualTo(1);

        Job j = reload(id);
        assertThat(j.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(j.getAttempts()).isEqualTo(1);
        assertThat(j.getResult()).containsEntry("handled", true);
        assertThat(j.getLockedBy()).isNull();
    }

    @Test
    void failingJobRetriesThenDeadLetters() {
        UUID id = insertPending("{\"fail\":true,\"failMessage\":\"boom\"}", 2);

        // Attempt 1: fails, not yet exhausted → rescheduled (PENDING, due now).
        engine.pollOnce();
        Job afterFirst = reload(id);
        assertThat(afterFirst.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);

        // Attempt 2: fails again, attempts == max_attempts → DEAD.
        engine.pollOnce();
        Job afterSecond = reload(id);
        assertThat(afterSecond.getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(afterSecond.getAttempts()).isEqualTo(2);
        assertThat(afterSecond.getLastError()).contains("boom");
    }

    @Test
    void jobNotYetDueIsNotClaimed() {
        UUID id = insertPendingDueInOneHour("{\"echo\":\"later\"}");

        assertThat(engine.pollOnce()).isZero();
        assertThat(reload(id).getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void reaperRequeuesAbandonedRunningJob() {
        UUID id = insertStaleRunning(1, 3);   // 1 attempt used, budget of 3

        assertThat(engine.reapStale()).isEqualTo(1);

        Job j = reload(id);
        assertThat(j.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(j.getLockedBy()).isNull();
    }

    @Test
    void reaperDeadLettersAbandonedJobWithNoAttemptsLeft() {
        UUID id = insertStaleRunning(3, 3);   // attempts already exhausted

        engine.reapStale();

        assertThat(reload(id).getStatus()).isEqualTo(JobStatus.DEAD);
    }

    @Test
    void concurrentWorkersNeverClaimTheSameJob() throws Exception {
        int n = 12;
        for (int i = 0; i < n; i++) {
            insertPending("{\"echo\":" + i + "}", 3);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<List<UUID>> claimer = () -> {
            start.await();
            return engine.claimBatch(n);
        };
        Future<List<UUID>> a = pool.submit(claimer);
        Future<List<UUID>> b = pool.submit(claimer);
        start.countDown();

        List<UUID> all = new ArrayList<>(a.get());
        List<UUID> bIds = b.get();
        pool.shutdown();

        // No id appears in both claims (SKIP LOCKED), and together they take all n.
        assertThat(all).doesNotContainAnyElementsOf(bIds);
        all.addAll(bIds);
        assertThat(all).hasSize(n);
        assertThat(all).doesNotHaveDuplicates();
    }

    // ---- helpers ------------------------------------------------------------

    private UUID insertPending(String payloadJson, int maxAttempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into jobs (id, queue_id, owner_id, status, payload, attempts, max_attempts, available_at) "
                        + "values (?, ?, ?, 'PENDING', ?::jsonb, 0, ?, now())",
                id, queueId, ownerId, payloadJson, maxAttempts);
        return id;
    }

    private UUID insertPendingDueInOneHour(String payloadJson) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into jobs (id, queue_id, owner_id, status, payload, attempts, max_attempts, available_at) "
                        + "values (?, ?, ?, 'PENDING', ?::jsonb, 0, 3, now() + interval '1 hour')",
                id, queueId, ownerId, payloadJson);
        return id;
    }

    private UUID insertStaleRunning(int attempts, int maxAttempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into jobs (id, queue_id, owner_id, status, attempts, max_attempts, available_at, "
                        + "locked_by, locked_at, heartbeat_at, started_at) "
                        + "values (?, ?, ?, 'RUNNING', ?, ?, now(), 'dead-worker', "
                        + "now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour')",
                id, queueId, ownerId, attempts, maxAttempts);
        return id;
    }

    private Job reload(UUID id) {
        return jobs.findById(id).orElseThrow();
    }
}
