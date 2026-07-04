-- V4: Create outbox_event table for Transactional Outbox pattern
CREATE TABLE outbox_event
(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);

-- Partial index: only unpublished rows — keeps the poller query fast
CREATE INDEX idx_outbox_event_unpublished
    ON outbox_event (created_at) WHERE published_at IS NULL;
