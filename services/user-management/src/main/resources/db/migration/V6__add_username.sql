ALTER TABLE users
    ADD COLUMN username VARCHAR(30);

UPDATE users
SET username = 'user_' || SUBSTRING(id::text, 1, 8)
WHERE username IS NULL;

CREATE UNIQUE INDEX uq_active_users_username
    ON users (username)
    WHERE deleted_at IS NULL;
