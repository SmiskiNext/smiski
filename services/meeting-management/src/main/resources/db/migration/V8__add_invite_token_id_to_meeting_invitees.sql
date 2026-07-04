-- V8__add_invite_token_id_to_meeting_invitees.sql
-- Links each MeetingInvitee record to its InviteToken record.
-- invite_token_id is nullable to preserve backward compatibility with existing invitees
-- that were created before this feature.

ALTER TABLE meeting_invitees
    ADD COLUMN invite_token_id UUID REFERENCES invite_tokens(id) ON DELETE SET NULL;
