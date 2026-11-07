package io.taskforge.jobs.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * In-process scheduler that drives {@link WorkerEngine}: one loop polls for work,
 * another reaps stale leases. Disabled via {@code worker.enabled=false} (tests
 * drive the engine directly for determinism). Each tick is guarded so a failure
 * is logged and the next tick still fires.
 *
 * Intervals come from the typed {@link WorkerProperties} Durations and are
 * registered programmatically — {@code @Scheduled(fixedDelayString)} only accepts
 * a millisecond number or ISO-8601 ("PT1S"), not the Boot-style "1s" we use in
 * config, so a SchedulingConfigurer lets us keep the readable config.
 *
 * Because claiming uses FOR UPDATE SKIP LOCKED, running this on several API
 * replicas is safe — they share the work without double-claiming.
 */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final WorkerEngine engine;
    private final WorkerProperties props;

    public JobWorker(WorkerEngine engine, WorkerProperties props) {
        this.engine = engine;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        log.info("Job worker enabled (workerId={}, poll={}, reap={})",
                engine.workerId(), props.pollInterval(), props.reapInterval());
        registrar.addFixedDelayTask(this::poll, props.pollInterval());
        registrar.addFixedDelayTask(this::reap, props.reapInterval());
    }

    void poll() {
        try {
            engine.pollOnce();
        } catch (Exception e) {
            log.error("Worker poll cycle failed", e);
        }
    }

    void reap() {
        try {
            engine.reapStale();
        } catch (Exception e) {
            log.error("Worker reap cycle failed", e);
        }
    }
}
