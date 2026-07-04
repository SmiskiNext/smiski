-- Password reset OTP tokens (single-use)
CREATE TABLE password_reset_tokens
(
    id         UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    user_id    UUID        NOT NULL,
    otp_hash   VARCHAR(64) NOT NULL, -- SHA-256 hex string
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    attempts   INT         NOT NULL DEFAULT 0, -- Wrong OTP attempts (lock after 5)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Index for finding valid (unused, not expired) tokens by user
CREATE INDEX idx_prt_user_expires ON password_reset_tokens (user_id, expires_at DESC)
    WHERE used_at IS NULL;

-- Rate limiting: track password reset request attempts
CREATE TABLE password_reset_attempts
(
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45), -- IPv4 or IPv6
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Index for rate limit queries by email
CREATE INDEX idx_pra_email_time ON password_reset_attempts (email, created_at DESC);

-- Index for rate limit queries by IP
CREATE INDEX idx_pra_ip_time ON password_reset_attempts (ip_address, created_at DESC);
