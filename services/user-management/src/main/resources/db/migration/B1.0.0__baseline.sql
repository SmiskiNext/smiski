-- Baseline schema for the B1.0.0 project phase.
-- Consolidates the legacy V1-V9 migrations into the final user-management schema.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users
(
    id            UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    full_name     VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(2048),
    preferences   JSONB,
    username      VARCHAR(30),
    google_uid    VARCHAR(128) UNIQUE,
    auth_provider VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    deleted_at    TIMESTAMPTZ  DEFAULT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_active_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_active_users_username ON users (username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_keyset ON users (created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_email_trgm ON users USING gin (email gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_username_trgm ON users USING gin (username gin_trgm_ops) WHERE deleted_at IS NULL;

CREATE TABLE refresh_tokens
(
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE outbox_event
(
    id             BIGSERIAL    NOT NULL PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE password_reset_tokens
(
    id                     UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    user_id                UUID        NOT NULL,
    otp_hash               VARCHAR(64) NOT NULL,
    expires_at             TIMESTAMPTZ NOT NULL,
    used_at                TIMESTAMPTZ,
    attempts               INT         NOT NULL DEFAULT 0,
    last_attempt_timestamp TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_prt_user_expires ON password_reset_tokens (user_id, expires_at DESC) WHERE used_at IS NULL;
CREATE INDEX idx_prt_last_attempt ON password_reset_tokens (user_id, last_attempt_timestamp DESC) WHERE used_at IS NULL;

CREATE TABLE password_reset_attempts
(
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pra_email_time ON password_reset_attempts (email, created_at DESC);
CREATE INDEX idx_pra_ip_time ON password_reset_attempts (ip_address, created_at DESC);
