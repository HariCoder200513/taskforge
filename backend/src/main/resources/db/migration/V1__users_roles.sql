-- Extensions: citext for case-insensitive email, pgcrypto for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users
CREATE TABLE users (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         citext      NOT NULL UNIQUE,
    password_hash text        NOT NULL,
    name          text        NOT NULL,
    status        text        NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- Roles
CREATE TABLE roles (
    id          smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        text     NOT NULL UNIQUE,
    description text
);

-- User <-> Role (composite PK, both FKs)
CREATE TABLE user_roles (
    user_id uuid     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id smallint NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

-- Seed the three fixed roles. ADMIN is only ever attached via the
-- seed/bootstrap command, never through the public API.
INSERT INTO roles (name, description) VALUES
    ('ADMIN',     'Full platform administration'),
    ('DEVELOPER', 'Submit and manage permitted jobs and queues'),
    ('VIEWER',    'Read-only access to permitted resources');
