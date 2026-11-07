package io.taskforge.jobs.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Worker-engine tunables ({@code worker.*} in application.yml).
 *
 * @param enabled         whether the in-process poller/reaper schedulers run.
 *                        Disabled in tests so the engine can be driven by hand.
 * @param pollInterval    delay between claim polls.
 * @param reapInterval    delay between stale-lease sweeps.
 * @param batchSize       max jobs claimed per poll.
 * @param retryBaseBackoff first-retry delay; doubles each subsequent attempt.
 * @param retryMaxBackoff cap on the exponential backoff.
 * @param leaseTimeout    a RUNNING job silent (no heartbeat) for longer than
 *                        this is treated as abandoned and requeued.
 */
@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(
        boolean enabled,
        Duration pollInterval,
        Duration reapInterval,
        int batchSize,
        Duration retryBaseBackoff,
        Duration retryMaxBackoff,
        Duration leaseTimeout
) {
    /** Exponential backoff for the Nth attempt (attempt >= 1), capped. */
    public Duration backoffFor(int attempt) {
        if (retryBaseBackoff == null || retryBaseBackoff.isZero() || retryBaseBackoff.isNegative()) {
            return Duration.ZERO;
        }
        int exponent = Math.min(Math.max(attempt - 1, 0), 16); // cap to avoid overflow
        Duration d = retryBaseBackoff.multipliedBy(1L << exponent);
        if (retryMaxBackoff != null && !retryMaxBackoff.isZero() && d.compareTo(retryMaxBackoff) > 0) {
            return retryMaxBackoff;
        }
        return d;
    }
}
