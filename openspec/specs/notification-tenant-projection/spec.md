# notification-tenant-projection Specification

## Purpose

Adds a PostgreSQL + Flyway persistence stack to the `notification` service and a
local `tenants` projection synced from `tenant.tenant.installed` /
`tenant.tenant.uninstalled` Kafka events. The projection stores each tenant's
`site_url` so the email content builder can construct Jira deep-links without a
cross-service lookup at send time.

## Requirements

### Requirement: PostgreSQL persistence stack in notification service

The `notification` service SHALL add Spring Data JPA, Flyway, and a PostgreSQL
driver to its dependency set. The `NotificationApplication` class SHALL remove
the explicit exclusions for `DataSourceAutoConfiguration`,
`HibernateJpaAutoConfiguration`, and `FlywayAutoConfiguration`. A `datasource`
block SHALL be added to `application.yaml` reading connection parameters from
environment variables (`POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`,
`POSTGRES_USER`, `POSTGRES_PASSWORD`). A `compose.yaml` SHALL be added to the
`notification` service directory to provide a local Postgres container for
development.

#### Scenario: Application starts with valid datasource config

- **WHEN** the notification service starts with a reachable Postgres instance
  and valid credentials
- **THEN** the application context loads successfully, Flyway applies the
  baseline, and all Kafka listeners start

#### Scenario: Application fails fast on missing datasource config

- **WHEN** the notification service starts with a missing or unreachable
  Postgres connection
- **THEN** the application fails to start with a clear configuration error
  rather than starting in a degraded state

### Requirement: Tenants projection baseline schema

The `notification` service SHALL create a `B1.0.0__baseline.sql` Flyway
migration containing a `tenants` table with the following columns:
`tenant_id VARCHAR(255) NOT NULL` (Jira cloudId, primary key),
`cloud_id VARCHAR(255) NOT NULL`, `site_url VARCHAR(512)`,
`status VARCHAR(20) NOT NULL` (CHECK IN `ACTIVE`, `SUSPENDED`, `UNINSTALLED`),
`updated_at TIMESTAMPTZ NOT NULL`, `uninstalled_at TIMESTAMPTZ`,
`purge_after TIMESTAMPTZ`. The table SHALL NOT be hash-partitioned (it is a
lookup table, not a business table).

#### Scenario: Baseline migration creates tenants table on clean database

- **WHEN** the notification service starts against a fresh Postgres database
- **THEN** Flyway applies `B1.0.0__baseline.sql` and the `tenants` table exists
  with the correct columns and primary key constraint

### Requirement: Consume tenant-installed event and upsert local projection

The `notification` service SHALL consume CloudEvents from the Kafka topic
`tenant.tenant.installed`, decode each event's `data` as a `TenantInstalled`
proto-JSON, and upsert a row in its local `tenants` table. The upsert SHALL set
`status = ACTIVE`, persist `site_url` (may be null if the proto field is blank),
and update `updated_at`. The consumer SHALL use a fixed, externally configurable
consumer group (`app.notification.kafka.tenant-installed-consumer-group`,
default `notification-tenant-installed`). Delivery failures SHALL be retried a
bounded number of times; exhausted retries SHALL route to
`tenant.tenant.installed.dlt`.

#### Scenario: New tenant row is inserted on first installed event

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  has no existing row in `notification`'s `tenants` table
- **THEN** a new row is inserted with `tenant_id = cloudId`, `status = ACTIVE`,
  and `site_url` populated from the proto field (or NULL if blank)

#### Scenario: Existing tenant row is updated on reinstall

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  already has a row
- **THEN** the existing row is overwritten: `status` becomes `ACTIVE`,
  `site_url` is refreshed, `updated_at` is updated; no duplicate row is created

#### Scenario: Blank site_url is stored as NULL

- **WHEN** the `TenantInstalled` proto's `site_url` field is empty or blank
- **THEN** the `tenants` row stores `NULL` in the `site_url` column

#### Scenario: Malformed event is retried then dead-lettered

- **WHEN** a `tenant.tenant.installed` message cannot be decoded into a valid
  `TenantInstalled` proto
- **THEN** the consumer retries up to the configured maximum; exhausted retries
  route to `tenant.tenant.installed.dlt` and the partition offset is committed

### Requirement: Consume tenant-uninstalled event and mark tenant inactive

The `notification` service SHALL consume CloudEvents from the Kafka topic
`tenant.tenant.uninstalled`, decode each event as a `TenantUninstalled`
proto-JSON, and upsert the corresponding `tenants` row with
`status = UNINSTALLED`, `uninstalled_at`, and `purge_after` from the proto. The
consumer SHALL use a fixed externally configurable consumer group
(`app.notification.kafka.tenant-uninstalled-consumer-group`, default
`notification-tenant-uninstalled`).

#### Scenario: Uninstalled event marks tenant inactive

- **WHEN** a `tenant.tenant.uninstalled` CloudEvent arrives for an existing
  `cloudId`
- **THEN** the `tenants` row is updated: `status = UNINSTALLED`,
  `uninstalled_at` and `purge_after` are set from the proto

#### Scenario: Uninstalled event for unknown tenant is a no-op

- **WHEN** a `tenant.tenant.uninstalled` CloudEvent arrives for a `cloudId` that
  has no row in the `tenants` table
- **THEN** no error is raised and no row is inserted; the event is acknowledged

### Requirement: Tenant site URL lookup for email content

The `notification` service's email content builder SHALL look up the `site_url`
for the email's `tenantId` from the local `tenants` projection before composing
the email body. When `site_url` is NULL or the `tenantId` has no row, the Jira
deep-link SHALL be omitted from the email body without causing an error or
preventing delivery.

#### Scenario: site_url present — deep-link included in email

- **WHEN** the email content builder looks up a `tenantId` that has a non-null
  `site_url` in the projection and the event also carries a non-blank `issueKey`
- **THEN** the email body contains a Jira deep-link constructed as
  `{siteUrl}/browse/{issueKey}`

#### Scenario: site_url absent — deep-link omitted gracefully

- **WHEN** the email content builder looks up a `tenantId` whose `site_url` is
  NULL, or the event carries no `issueKey`
- **THEN** the email body is sent without a Jira deep-link; no error is raised
  and the email is still delivered
