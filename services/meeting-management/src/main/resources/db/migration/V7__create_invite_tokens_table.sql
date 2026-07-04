-- V7__create_invite_tokens_table.sql
-- Stores per-invitee HMAC-SHA256 invite tokens with status tracking and expiry.
-- Token hashes (SHA-256 of raw token) are stored for revocation lookups.
-- Raw token is never persisted; it is only ever transmitted to the invitee via email.

CREATE TABLE invite_tokens (
    id          UUID        NOT NULL PRIMARY KEY,
    meeting_id  UUID        NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    invitee_id  UUID        NOT NULL REFERENCES meeting_invitees(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT invite_tokens_status_check CHECK (status IN ('PENDING', 'USED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT invite_tokens_expires_at_check CHECK (expires_at > NOW())
);

-- Index for fast lookup of tokens by meeting and status (used by invalidation)
CREATE INDEX idx_invite_tokens_meeting_status ON invite_tokens(meeting_id, status);

-- Index for fast lookup by token hash (used by validation)
CREATE INDEX idx_invite_tokens_token_hash ON invite_tokens(token_hash);
