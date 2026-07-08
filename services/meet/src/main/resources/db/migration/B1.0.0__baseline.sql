-- Baseline schema for the B1.0.0 project phase (multi-tenant, partitioned).
--
-- Distribution: every business table is PARTITION BY HASH (tenant_id) with 16 partitions.
-- tenant_id is the Jira cloudId (stable across reinstall) and is the leading column of
-- every primary key, unique constraint and foreign key so partition pruning applies and
-- all per-tenant joins stay local to a single partition.
--
-- tenants is a local read-model projection synchronised from the tenant service via Kafka.
-- It is small (one row per site) and is NOT partitioned; business tables reference it to
-- guarantee the tenant exists before any meeting is written.

-- ============================================================================
-- tenants (projection — synchronised from the tenant service)
-- ============================================================================

CREATE TABLE tenants
(
    tenant_id  VARCHAR(255) NOT NULL,                 -- Jira cloudId
    cloud_id   VARCHAR(255) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'UNINSTALLED')),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenants PRIMARY KEY (tenant_id)
);

CREATE INDEX idx_tenants_status ON tenants (status);

-- ============================================================================
-- meetings
-- ============================================================================

CREATE TABLE meetings
(
    tenant_id   VARCHAR(255) NOT NULL,
    id          UUID         NOT NULL DEFAULT uuidv7(),
    host_id     VARCHAR(128) NOT NULL,                 -- Jira accountId
    short_code  VARCHAR(15)  NOT NULL,
    issue_id    VARCHAR(64),                           -- Jira Issue id
    issue_key   VARCHAR(64),                           -- Jira Issue key, e.g. "PROJ-123"
    project_key VARCHAR(64),
    title       VARCHAR(255),
    description TEXT,
    start_time  TIMESTAMPTZ,
    end_time    TIMESTAMPTZ,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('INSTANT', 'SCHEDULED')),
    status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
    settings    JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_meetings PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_meetings_short_code UNIQUE (tenant_id, short_code),
    CONSTRAINT fk_meetings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id)
) PARTITION BY HASH (tenant_id);

CREATE TABLE meetings_p00 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE meetings_p01 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE meetings_p02 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE meetings_p03 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE meetings_p04 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE meetings_p05 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE meetings_p06 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE meetings_p07 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE meetings_p08 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE meetings_p09 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE meetings_p10 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE meetings_p11 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE meetings_p12 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE meetings_p13 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE meetings_p14 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE meetings_p15 PARTITION OF meetings FOR VALUES WITH (MODULUS 16, REMAINDER 15);

CREATE INDEX idx_meetings_issue ON meetings (tenant_id, issue_id) WHERE issue_id IS NOT NULL;
CREATE INDEX idx_meetings_host ON meetings (tenant_id, host_id);
CREATE INDEX idx_meetings_status ON meetings (tenant_id, status);
CREATE INDEX idx_meetings_keyset ON meetings (tenant_id, created_at DESC, id DESC);

-- ============================================================================
-- participation_logs (UUIDv7 id — no global sequence)
-- ============================================================================

CREATE TABLE participation_logs
(
    tenant_id               VARCHAR(255) NOT NULL,
    id                      UUID         NOT NULL DEFAULT uuidv7(),
    meeting_id              UUID         NOT NULL,
    account_id              VARCHAR(128) NOT NULL,     -- Jira accountId (required — no guests)
    display_name            VARCHAR(255),
    display_name_cached_at  TIMESTAMPTZ,
    role                    VARCHAR(20)  NOT NULL CHECK (role IN ('HOST', 'PARTICIPANT')),
    livekit_identity        VARCHAR(255) NOT NULL,
    livekit_participant_sid VARCHAR(50),
    joined_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    left_at                 TIMESTAMPTZ,
    CONSTRAINT pk_participation_logs PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_participation_meeting
        FOREIGN KEY (tenant_id, meeting_id) REFERENCES meetings (tenant_id, id) ON DELETE CASCADE
) PARTITION BY HASH (tenant_id);

CREATE TABLE participation_logs_p00 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE participation_logs_p01 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE participation_logs_p02 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE participation_logs_p03 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE participation_logs_p04 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE participation_logs_p05 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE participation_logs_p06 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE participation_logs_p07 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE participation_logs_p08 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE participation_logs_p09 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE participation_logs_p10 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE participation_logs_p11 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE participation_logs_p12 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE participation_logs_p13 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE participation_logs_p14 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE participation_logs_p15 PARTITION OF participation_logs FOR VALUES WITH (MODULUS 16, REMAINDER 15);

CREATE INDEX idx_participation_meeting
    ON participation_logs (tenant_id, meeting_id, joined_at DESC);
CREATE INDEX idx_participation_account
    ON participation_logs (tenant_id, account_id, joined_at DESC);
CREATE INDEX idx_participation_active_identity
    ON participation_logs (tenant_id, meeting_id, livekit_identity) WHERE left_at IS NULL;
CREATE UNIQUE INDEX uq_participation_active_sid
    ON participation_logs (tenant_id, livekit_participant_sid)
    WHERE left_at IS NULL AND livekit_participant_sid IS NOT NULL;

-- ============================================================================
-- meeting_invitees
-- ============================================================================

CREATE TABLE meeting_invitees
(
    tenant_id       VARCHAR(255) NOT NULL,
    id              UUID         NOT NULL DEFAULT uuidv7(),
    meeting_id      UUID         NOT NULL,
    inviter_id      VARCHAR(128) NOT NULL,             -- Jira accountId
    account_id      VARCHAR(128),                      -- Jira accountId (null if not resolved)
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    invite_token_id UUID,
    invited_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    responded_at    TIMESTAMPTZ,
    CONSTRAINT pk_meeting_invitees PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_meeting_invitees_meeting_email UNIQUE (tenant_id, meeting_id, email),
    CONSTRAINT fk_meeting_invitees_meeting
        FOREIGN KEY (tenant_id, meeting_id) REFERENCES meetings (tenant_id, id) ON DELETE CASCADE
) PARTITION BY HASH (tenant_id);

CREATE TABLE meeting_invitees_p00 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE meeting_invitees_p01 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE meeting_invitees_p02 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE meeting_invitees_p03 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE meeting_invitees_p04 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE meeting_invitees_p05 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE meeting_invitees_p06 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE meeting_invitees_p07 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE meeting_invitees_p08 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE meeting_invitees_p09 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE meeting_invitees_p10 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE meeting_invitees_p11 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE meeting_invitees_p12 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE meeting_invitees_p13 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE meeting_invitees_p14 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE meeting_invitees_p15 PARTITION OF meeting_invitees FOR VALUES WITH (MODULUS 16, REMAINDER 15);

CREATE INDEX idx_meeting_invitees_meeting ON meeting_invitees (tenant_id, meeting_id);
CREATE INDEX idx_meeting_invitees_email ON meeting_invitees (tenant_id, email);
CREATE INDEX idx_meeting_invitees_account
    ON meeting_invitees (tenant_id, account_id) WHERE account_id IS NOT NULL;

-- ============================================================================
-- invite_tokens
-- ============================================================================

CREATE TABLE invite_tokens
(
    tenant_id  VARCHAR(255) NOT NULL,
    id         UUID         NOT NULL DEFAULT uuidv7(),
    meeting_id UUID         NOT NULL,
    invitee_id UUID         NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'USED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_invite_tokens PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_invite_tokens_hash UNIQUE (tenant_id, token_hash),
    CONSTRAINT fk_invite_tokens_meeting
        FOREIGN KEY (tenant_id, meeting_id) REFERENCES meetings (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_invite_tokens_invitee
        FOREIGN KEY (tenant_id, invitee_id) REFERENCES meeting_invitees (tenant_id, id) ON DELETE CASCADE
) PARTITION BY HASH (tenant_id);

CREATE TABLE invite_tokens_p00 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE invite_tokens_p01 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE invite_tokens_p02 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE invite_tokens_p03 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE invite_tokens_p04 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE invite_tokens_p05 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE invite_tokens_p06 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE invite_tokens_p07 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE invite_tokens_p08 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE invite_tokens_p09 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE invite_tokens_p10 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE invite_tokens_p11 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE invite_tokens_p12 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE invite_tokens_p13 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE invite_tokens_p14 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE invite_tokens_p15 PARTITION OF invite_tokens FOR VALUES WITH (MODULUS 16, REMAINDER 15);

ALTER TABLE meeting_invitees
    ADD CONSTRAINT fk_meeting_invitees_token
        FOREIGN KEY (tenant_id, invite_token_id) REFERENCES invite_tokens (tenant_id, id) ON DELETE SET NULL;

CREATE INDEX idx_invite_tokens_meeting_status ON invite_tokens (tenant_id, meeting_id, status);

-- ============================================================================
-- outbox_event (BIGSERIAL -> UUIDv7; poller scans globally, tenant-scoped PK)
-- ============================================================================

CREATE TABLE outbox_event
(
    tenant_id      VARCHAR(255) NOT NULL,
    id             UUID         NOT NULL DEFAULT uuidv7(),
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    CONSTRAINT pk_outbox_event PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
