-- Worker/queue engine columns on jobs: lease (claim) fields for FOR UPDATE
-- SKIP LOCKED, retry/backoff scheduling, dead-letter bookkeeping and result.
--
-- Claim model: a worker atomically moves a claimable row (PENDING and due,
-- i.e. available_at <= now()) to RUNNING under FOR UPDATE SKIP LOCKED, stamping
-- locked_by/locked_at/heartbeat_at. On failure it either reschedules (PENDING
-- with a future available_at = backoff) or, once attempts hit max_attempts,
-- moves it to DEAD. A reaper requeues RUNNING rows whose heartbeat is stale
-- (the worker crashed mid-run).

ALTER TABLE jobs
    ADD COLUMN attempts     integer     NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts integer     NOT NULL DEFAULT 3,
    ADD COLUMN available_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN locked_by    text,
    ADD COLUMN locked_at    timestamptz,
    ADD COLUMN heartbeat_at timestamptz,
    ADD COLUMN started_at   timestamptz,
    ADD COLUMN finished_at  timestamptz,
    ADD COLUMN last_error   text,
    ADD COLUMN result       jsonb;

-- Claim scan: only due, pending rows, ordered by when they became available.
-- Partial index keeps it tiny regardless of how many finished jobs pile up.
CREATE INDEX idx_jobs_claimable ON jobs (available_at)
    WHERE status = 'PENDING';

-- Reaper scan: running rows ordered by last heartbeat.
CREATE INDEX idx_jobs_running_heartbeat ON jobs (heartbeat_at)
    WHERE status = 'RUNNING';
