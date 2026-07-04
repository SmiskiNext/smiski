-- V5: Add description to meetings and create meeting_invitees table

-- Add description column to meetings
ALTER TABLE meetings
    ADD COLUMN description TEXT;

-- Create meeting_invitees table
CREATE TABLE meeting_invitees
(
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL,
    user_id UUID,
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT pk_meeting_invitees PRIMARY KEY (id),
    CONSTRAINT uq_meeting_invitees_meeting_email UNIQUE (meeting_id, email),
    CONSTRAINT chk_meeting_invitees_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE INDEX idx_meeting_invitees_meeting_id ON meeting_invitees (meeting_id);
CREATE INDEX idx_meeting_invitees_email ON meeting_invitees (email);
CREATE INDEX idx_meeting_invitees_user_id ON meeting_invitees (user_id) WHERE user_id IS NOT NULL;
