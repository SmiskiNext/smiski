-- Add composite partial index to support keyset (cursor) pagination on the users table.
-- Ordered by (created_at DESC, id DESC) to match the ORDER BY clause in the keyset query.
-- Partial index (WHERE deleted_at IS NULL) keeps the index small and aligned with query filters.
CREATE INDEX idx_users_keyset ON users (created_at DESC, id DESC) WHERE deleted_at IS NULL;

-- Add trigram GIN indexes for efficient case-insensitive substring search (ILIKE) on email and
-- username. Requires the pg_trgm extension (available in all managed PostgreSQL services).
-- Without these indexes, ILIKE performs a sequential scan on every search request.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_users_email_trgm ON users USING gin (email gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_username_trgm ON users USING gin (username gin_trgm_ops) WHERE deleted_at IS NULL;

