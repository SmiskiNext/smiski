-- V2: Create recordings table
CREATE TABLE recordings
(
    id UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    -- LiveKit egress tracking
    livekit_egress_id VARCHAR(50) UNIQUE,
    livekit_room_name VARCHAR(255),
    -- File info (populated by egress_ended webhook)
    file_url          VARCHAR(2048),
    thumbnail_url     VARCHAR(2048),
    storage_path      VARCHAR(2048),
    -- Status: PENDING → RECORDING → COMPLETED | FAILED
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RECORDING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    duration_seconds  INT         NOT NULL DEFAULT 0,
    file_size_bytes   BIGINT      NOT NULL DEFAULT 0,
    error_message     VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recordings_meeting_id ON recordings (meeting_id);
CREATE INDEX idx_recordings_status ON recordings (status);
CREATE INDEX idx_recordings_egress_id ON recordings (livekit_egress_id) WHERE livekit_egress_id IS NOT NULL;

-- Enforce at most one active (PENDING or RECORDING) recording per meeting
CREATE UNIQUE INDEX uq_recordings_active_per_meeting
    ON recordings (meeting_id) WHERE status IN ('PENDING', 'RECORDING');
