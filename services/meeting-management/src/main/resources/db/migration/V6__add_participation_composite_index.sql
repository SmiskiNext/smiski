CREATE INDEX idx_participation_logs_user_joined_meeting
    ON participation_logs (user_id, joined_at DESC, meeting_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_participation_logs_meeting_user_joined
    ON participation_logs (meeting_id, user_id, joined_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_participation_logs_meeting_guest_joined
    ON participation_logs (meeting_id, display_name, joined_at DESC)
    WHERE user_id IS NULL;
