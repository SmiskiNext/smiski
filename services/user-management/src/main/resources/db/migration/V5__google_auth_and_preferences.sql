-- V5: Google auth support — make password_hash nullable, add google_uid and auth_provider

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN google_uid    VARCHAR(128) UNIQUE,
    ADD COLUMN auth_provider VARCHAR(20)  NOT NULL DEFAULT 'EMAIL';
