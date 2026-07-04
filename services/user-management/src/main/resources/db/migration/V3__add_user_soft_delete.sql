ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE users
    DROP CONSTRAINT uq_users_email;

CREATE UNIQUE INDEX uq_active_users_email ON users (email) WHERE deleted_at IS NULL;
