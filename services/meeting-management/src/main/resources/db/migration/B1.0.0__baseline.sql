-- Baseline schema for the B1.0.0 project phase.
-- Consolidates the legacy V1-V9 migrations into the final meeting-management schema.

CREATE TABLE meetings
(
    id          UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    host_id     UUID        NOT NULL,
    short_code  VARCHAR(15) NOT NULL,
    title       VARCHAR(255),
    description TEXT,
    start_time  TIMESTAMPTZ,
    end_time    TIMESTAMPTZ,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('INSTANT', 'SCHEDULED')),
    status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
    settings    JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_meetings_short_code UNIQUE (short_code)
);

CREATE INDEX idx_meetings_host_id ON meetings (host_id);
CREATE INDEX idx_meetings_status ON meetings (status);
CREATE INDEX idx_meetings_keyset ON meetings (created_at DESC, id DESC);

CREATE TABLE recordings
(
    id                UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    meeting_id        UUID        NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    livekit_egress_id VARCHAR(50) UNIQUE,
    livekit_room_name VARCHAR(255),
    file_url          VARCHAR(2048),
    thumbnail_url     VARCHAR(2048),
    storage_path      VARCHAR(2048),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RECORDING', 'COMPLETED', 'FAILED')),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    duration_seconds  INT         NOT NULL DEFAULT 0,
    file_size_bytes   BIGINT      NOT NULL DEFAULT 0,
    error_message     VARCHAR(1024),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recordings_meeting_id ON recordings (meeting_id);
CREATE INDEX idx_recordings_status ON recordings (status);
CREATE INDEX idx_recordings_egress_id ON recordings (livekit_egress_id) WHERE livekit_egress_id IS NOT NULL;
CREATE UNIQUE INDEX uq_recordings_active_per_meeting
    ON recordings (meeting_id) WHERE status IN ('PENDING', 'RECORDING');

CREATE TABLE participation_logs
(
    id                      BIGSERIAL    NOT NULL PRIMARY KEY,
    meeting_id              UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    user_id                 UUID,
    display_name            VARCHAR(255) NOT NULL,
    role                    VARCHAR(20)  NOT NULL CHECK (role IN ('HOST', 'PARTICIPANT', 'GUEST')),
    livekit_identity        VARCHAR(255) NOT NULL,
    livekit_participant_sid VARCHAR(50),
    joined_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    left_at                 TIMESTAMPTZ
);

CREATE INDEX idx_participation_logs_meeting_id ON participation_logs (meeting_id);
CREATE INDEX idx_participation_logs_user_id ON participation_logs (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_participation_logs_joined_at ON participation_logs (joined_at DESC);
CREATE INDEX idx_participation_logs_active_identity
    ON participation_logs (meeting_id, livekit_identity) WHERE left_at IS NULL;
CREATE UNIQUE INDEX uq_participation_logs_active_sid
    ON participation_logs (livekit_participant_sid) WHERE left_at IS NULL AND livekit_participant_sid IS NOT NULL;
CREATE INDEX idx_participation_logs_user_joined_meeting
    ON participation_logs (user_id, joined_at DESC, meeting_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_participation_logs_meeting_user_joined
    ON participation_logs (meeting_id, user_id, joined_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_participation_logs_meeting_guest_joined
    ON participation_logs (meeting_id, display_name, joined_at DESC) WHERE user_id IS NULL;

CREATE TABLE outbox_event
(
    id             BIGSERIAL    NOT NULL PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE meeting_invitees
(
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    meeting_id   UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    inviter_id   UUID         NOT NULL,
    user_id      UUID,
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    invited_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT pk_meeting_invitees PRIMARY KEY (id),
    CONSTRAINT uq_meeting_invitees_meeting_email UNIQUE (meeting_id, email),
    CONSTRAINT chk_meeting_invitees_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE INDEX idx_meeting_invitees_meeting_id ON meeting_invitees (meeting_id);
CREATE INDEX idx_meeting_invitees_email ON meeting_invitees (email);
CREATE INDEX idx_meeting_invitees_user_id ON meeting_invitees (user_id) WHERE user_id IS NOT NULL;

CREATE TABLE invite_tokens
(
    id         UUID        NOT NULL PRIMARY KEY,
    meeting_id UUID        NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    invitee_id UUID        NOT NULL REFERENCES meeting_invitees (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invite_tokens_status_check CHECK (status IN ('PENDING', 'USED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_invite_tokens_meeting_status ON invite_tokens (meeting_id, status);
CREATE INDEX idx_invite_tokens_token_hash ON invite_tokens (token_hash);

ALTER TABLE meeting_invitees
    ADD COLUMN invite_token_id UUID REFERENCES invite_tokens (id) ON DELETE SET NULL;
