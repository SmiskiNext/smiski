-- Add last_attempt_timestamp column to password_reset_tokens table
ALTER TABLE password_reset_tokens
    ADD COLUMN last_attempt_timestamp TIMESTAMPTZ;

-- Add index for progressive delay queries
CREATE INDEX idx_prt_last_attempt ON password_reset_tokens (user_id, last_attempt_timestamp DESC)
    WHERE used_at IS NULL;
