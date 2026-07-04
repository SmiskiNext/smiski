-- V3: Create participation_logs table
CREATE TABLE participation_logs
(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    user_id UUID, -- NULL for guest participants
    display_name            VARCHAR(255) NOT NULL,
    role                    VARCHAR(20)  NOT NULL CHECK (role IN ('HOST', 'PARTICIPANT', 'GUEST')),
    -- LiveKit JWT sub claim: "userId:deviceId" for authenticated, "guest:deviceId" for guests
    livekit_identity        VARCHAR(255) NOT NULL,
    -- LiveKit session ID "PA_xxx", set when participant_joined webhook arrives
    livekit_participant_sid VARCHAR(50),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ
);

CREATE INDEX idx_participation_logs_meeting_id ON participation_logs (meeting_id);
CREATE INDEX idx_participation_logs_user_id ON participation_logs (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_participation_logs_joined_at ON participation_logs (joined_at DESC);

-- Lookup by identity for webhook participant_joined (to assign sid)
CREATE INDEX idx_participation_logs_active_identity
    ON participation_logs (meeting_id, livekit_identity) WHERE left_at IS NULL;

-- Unique active sid for webhook participant_left lookup
CREATE UNIQUE INDEX uq_participation_logs_active_sid
    ON participation_logs (livekit_participant_sid) WHERE left_at IS NULL AND livekit_participant_sid IS NOT NULL;
