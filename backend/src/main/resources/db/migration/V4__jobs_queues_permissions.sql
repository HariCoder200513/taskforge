-- Ownership + permission foundation for RBAC and the job-visibility rule.
-- Job/queue engine-specific columns (visibility timeout, attempts, DLQ links,
-- scheduling) are added in later migrations with their milestones. workspace_id
-- is added now (nullable) so the future multi-tenancy milestone is not a
-- painful migration.

CREATE TABLE queues (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name         text        NOT NULL UNIQUE,
    owner_id     uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    workspace_id uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE jobs (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id     uuid        NOT NULL REFERENCES queues(id) ON DELETE RESTRICT,
    owner_id     uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    workspace_id uuid,
    status       text        NOT NULL DEFAULT 'PENDING',
    payload      jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_jobs_queue_id ON jobs(queue_id);
CREATE INDEX idx_jobs_owner_id ON jobs(owner_id);

-- Per-user, per-queue grants. Job visibility is derived from ownership
-- (owner_id) OR an appropriate queue permission (VIEW or higher), OR ADMIN.
CREATE TABLE queue_permissions (
    queue_id   uuid NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    permission text NOT NULL CHECK (permission IN ('VIEW', 'SUBMIT', 'MANAGE', 'RETRY')),
    PRIMARY KEY (queue_id, user_id, permission)
);

CREATE INDEX idx_queue_permissions_user ON queue_permissions(user_id);
