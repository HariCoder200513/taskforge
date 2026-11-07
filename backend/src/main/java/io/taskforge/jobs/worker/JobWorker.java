package io.taskforge.jobs.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process scheduler that drives {@link WorkerEngine}: one loop polls for work,
 * another reaps stale leases. Disabled via {@code worker.enabled=false} (tests
 * drive the engine directly for determinism). Each tick is guarded so a failure
 * is logged and the next tick still fires.
 *
 * Because claiming uses FOR UPDATE SKIP LOCKED, running this on several API
 * replicas is safe — they share the work without double-claiming.
 */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final WorkerEngine engine;

    public JobWorker(WorkerEngine engine) {
        this.engine = engine;
        log.info("Job worker enabled (workerId={})", engine.workerId());
    }

    @Scheduled(fixedDelayString = "${worker.poll-interval:1s}")
    public void poll() {
        try {
            engine.pollOnce();
        } catch (Exception e) {
            log.error("Worker poll cycle failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${worker.reap-interval:30s}")
    public void reap() {
        try {
            engine.reapStale();
        } catch (Exception e) {
            log.error("Worker reap cycle failed", e);
        }
    }
}
