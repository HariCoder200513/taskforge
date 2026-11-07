package io.taskforge.domain;

/** Lifecycle of a job. Submission creates it as PENDING; the queue/worker
 *  engine (later milestones) drives the rest. */
public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DEAD
}
