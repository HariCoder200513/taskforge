-- Append-only audit log. user_id is nullable because failed logins have no
-- authenticated user. UPDATE/DELETE will be denied to the application DB role
-- via a GRANT migration once that role exists (deployment milestone); the
-- application never mutates rows here.
CREATE TABLE audit_logs (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        REFERENCES users(id) ON DELETE SET NULL,
    action        text        NOT NULL,
    resource_type text,
    resource_id   text,
    ip            inet,
    request_id    text,
    metadata      jsonb,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_created ON audit_logs(user_id, created_at);
