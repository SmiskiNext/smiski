-- V9__drop_invite_tokens_expires_at_check.sql
-- Drops the CHECK (expires_at > NOW()) constraint from invite_tokens.
-- This constraint is evaluated on UPDATE as well as INSERT, causing failures
-- when revoking tokens whose expiry has already passed.
ALTER TABLE invite_tokens DROP CONSTRAINT invite_tokens_expires_at_check;
