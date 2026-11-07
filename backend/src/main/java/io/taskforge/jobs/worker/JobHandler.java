package io.taskforge.jobs.worker;

import io.taskforge.domain.Job;

import java.util.Map;

/**
 * Executes the actual work of a job. The engine owns the lifecycle (claim,
 * retry, DLQ); a handler only runs the payload and returns a result, or throws
 * to signal failure (which the engine turns into a retry or dead-letter).
 *
 * Implementations must be side-effect-safe to re-run: a job can be retried, and
 * the reaper can requeue a job whose worker died mid-run, so the same payload
 * may execute more than once.
 */
public interface JobHandler {

    /** Run the job. Return a JSON-serializable result, or throw on failure. */
    Map<String, Object> handle(Job job) throws Exception;
}
