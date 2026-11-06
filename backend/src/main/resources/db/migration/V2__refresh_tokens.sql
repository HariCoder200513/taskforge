-- Opaque refresh tokens, stored as SHA-256 hashes. Rotation lineage is
-- tracked via family_id + replaced_by so reuse of a rotated token can
-- revoke the whole family (replay detection).
CREATE TABLE refresh_tokens (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash          text        NOT NULL UNIQUE,
    family_id           uuid        NOT NULL,
    expires_at          timestamptz NOT NULL,          -- sliding expiry (14d)
    absolute_expires_at timestamptz NOT NULL,          -- hard session cap (30d)
    used_at             timestamptz,                   -- set when rotated
    replaced_by         uuid        REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    revoked_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    user_agent          text,
    ip                  text                           -- real client IP (trusted forwarded header)
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id  ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
