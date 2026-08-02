-- Baseline schema for the notification service tenant projection.
--
-- tenants is a local read-model projection synchronised from the tenant service via Kafka.
-- It stores one row per Atlassian site so the notification service can resolve site_url
-- for Jira deep-links in calendar emails without a synchronous cross-service call.
-- ============================================================================
-- tenants (projection — synchronised from the tenant service)
-- ============================================================================
CREATE TABLE tenants (
    tenant_id VARCHAR(255) NOT NULL,
    cloud_id VARCHAR(255) NOT NULL,
    site_url VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'UNINSTALLED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now (),
    uninstalled_at TIMESTAMPTZ,
    purge_after TIMESTAMPTZ,
    CONSTRAINT pk_tenants PRIMARY KEY (tenant_id)
);

CREATE INDEX idx_tenants_status ON tenants (status);
