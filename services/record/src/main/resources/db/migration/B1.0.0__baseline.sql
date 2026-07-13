-- Baseline schema for the record service (B1.0.0 phase, multi-tenant, partitioned).
--
-- The record service owns its own database (zms_recordings) and is fully independent
-- of the meeting service: there is NO cross-service foreign key from recordings to
-- meetings. meeting_id is kept as a plain UUID column so recordings can still be
-- correlated with a meeting without coupling the two schemas.
--
-- Distribution: every business table is PARTITION BY HASH (tenant_id) with 16 partitions.
-- tenant_id is the Jira cloudId (stable across reinstall) and is the leading column of
-- every primary key and unique constraint so partition pruning applies and all per-tenant
-- queries stay local to a single partition.
--
-- tenants is a local read-model projection synchronised from the tenant service via Kafka.
-- It is small (one row per site) and is NOT partitioned.
-- ============================================================================
-- tenants (projection — synchronised from the tenant service)
-- ============================================================================
CREATE TABLE tenants (
    tenant_id VARCHAR(255) NOT NULL, -- Jira cloudId
    cloud_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'UNINSTALLED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now (),
    uninstalled_at TIMESTAMPTZ,
    purge_after TIMESTAMPTZ,
    CONSTRAINT pk_tenants PRIMARY KEY (tenant_id)
);

CREATE INDEX idx_tenants_status ON tenants (status);

-- ============================================================================
-- recordings (editable metadata: title, notes, soft-delete)
-- ============================================================================
CREATE TABLE recordings (
    tenant_id VARCHAR(255) NOT NULL,
    id UUID NOT NULL DEFAULT uuidv7 (),
    meeting_id UUID NOT NULL,
    livekit_egress_id VARCHAR(50),
    livekit_room_name VARCHAR(255),
    file_url VARCHAR(2048),
    thumbnail_url VARCHAR(2048),
    storage_path VARCHAR(2048),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'RECORDING', 'COMPLETED', 'FAILED')
    ),
    title VARCHAR(255), -- user-editable
    notes TEXT, -- user-editable
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(128),
    purge_after TIMESTAMPTZ,
    edited_by VARCHAR(128), -- Jira accountId of last editor
    edited_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now (),
    ended_at TIMESTAMPTZ,
    duration_seconds INT NOT NULL DEFAULT 0,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now (),
    CONSTRAINT pk_recordings PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_recordings_egress UNIQUE (tenant_id, livekit_egress_id)
)
PARTITION BY
    HASH (tenant_id);

CREATE TABLE recordings_p00 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 0);

CREATE TABLE recordings_p01 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 1);

CREATE TABLE recordings_p02 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 2);

CREATE TABLE recordings_p03 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 3);

CREATE TABLE recordings_p04 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 4);

CREATE TABLE recordings_p05 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 5);

CREATE TABLE recordings_p06 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 6);

CREATE TABLE recordings_p07 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 7);

CREATE TABLE recordings_p08 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 8);

CREATE TABLE recordings_p09 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 9);

CREATE TABLE recordings_p10 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 10);

CREATE TABLE recordings_p11 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 11);

CREATE TABLE recordings_p12 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 12);

CREATE TABLE recordings_p13 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 13);

CREATE TABLE recordings_p14 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 14);

CREATE TABLE recordings_p15 PARTITION OF recordings FOR
VALUES
WITH
    (MODULUS 16, REMAINDER 15);

CREATE INDEX idx_recordings_meeting ON recordings (tenant_id, meeting_id)
WHERE
    deleted_at IS NULL;

CREATE INDEX idx_recordings_status ON recordings (tenant_id, status);

CREATE UNIQUE INDEX uq_recordings_active_per_meeting ON recordings (tenant_id, meeting_id)
WHERE
    status IN ('PENDING', 'RECORDING');

CREATE INDEX idx_recordings_purge ON recordings (tenant_id, purge_after)
WHERE
    deleted_at IS NOT NULL;

-- ============================================================================
-- outbox_event (UUIDv7 id; poller scans globally, tenant-scoped PK)
-- ============================================================================
CREATE TABLE outbox_event (
    tenant_id VARCHAR(255) NOT NULL,
    id UUID NOT NULL DEFAULT uuidv7 (),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now (),
    published_at TIMESTAMPTZ,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    CONSTRAINT pk_outbox_event PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at)
WHERE
    published_at IS NULL;
