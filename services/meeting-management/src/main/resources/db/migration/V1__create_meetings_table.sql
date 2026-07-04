-- V1: Create meetings table
CREATE TABLE meetings
(
    id UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    host_id UUID NOT NULL,
    short_code VARCHAR(15) NOT NULL,
    title      VARCHAR(255),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    type       VARCHAR(20) NOT NULL CHECK (type IN ('INSTANT', 'SCHEDULED')),
    status     VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
    settings JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_meetings_short_code UNIQUE (short_code)
);

CREATE INDEX idx_meetings_host_id ON meetings (host_id);
CREATE INDEX idx_meetings_status ON meetings (status);
-- Keyset pagination index (created_at DESC, id DESC)
CREATE INDEX idx_meetings_keyset ON meetings (created_at DESC, id DESC);
