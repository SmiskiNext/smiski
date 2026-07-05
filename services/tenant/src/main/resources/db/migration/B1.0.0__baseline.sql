-- Baseline schema for the tenant service (B1.0.0 project phase).
-- Source of truth for Forge app installations keyed by Jira cloudId.
--
-- Lifecycle events (avi:forge:installed:app, avi:forge:upgraded:app, preUninstall)
-- are delivered via Forge Remote and upserted here. Each state change is emitted to
-- Kafka through the transactional outbox so downstream services (meeting-management)
-- can maintain their own tenant projection.

CREATE TABLE tenants
(
    tenant_id            VARCHAR(255) NOT NULL,               -- Jira cloudId (stable across reinstall)
    installation_id      VARCHAR(255) NOT NULL,               -- Forge installation id (changes on reinstall)
    app_id               VARCHAR(255) NOT NULL,               -- Forge application id (ari)
    environment_type     VARCHAR(20)  NOT NULL DEFAULT 'PRODUCTION'
        CHECK (environment_type IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION')),
    environment_id       VARCHAR(255),
    site_url             VARCHAR(512),
    installer_account_id VARCHAR(128),
    app_version          VARCHAR(50),
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'UNINSTALLED')),
    installed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    uninstalled_at       TIMESTAMPTZ,
    CONSTRAINT pk_tenants PRIMARY KEY (tenant_id),
    CONSTRAINT uq_tenants_installation UNIQUE (installation_id)
);

CREATE INDEX idx_tenants_status ON tenants (status);
CREATE INDEX idx_tenants_app_id ON tenants (app_id);

CREATE TABLE outbox_event
(
    tenant_id      VARCHAR(255) NOT NULL,
    id             UUID         NOT NULL DEFAULT uuidv7(),
    aggregate_id   VARCHAR(255) NOT NULL,
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
