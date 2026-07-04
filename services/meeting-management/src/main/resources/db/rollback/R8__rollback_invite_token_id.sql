-- Rollback for V8__add_invite_token_id_to_meeting_invitees.sql
ALTER TABLE meeting_invitees DROP COLUMN IF EXISTS invite_token_id;
