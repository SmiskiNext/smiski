## ADDED Requirements

### Requirement: Consume tenant-installed event and upsert local projection

The `meet` service SHALL consume CloudEvents from the Kafka topic
`tenant.tenant.installed` and upsert a row into its local `tenants` projection
table. Each consumed event SHALL be decoded from CloudEvents 1.0 structured JSON
whose `data` is a `TenantInstalled` proto message rendered as proto-JSON (per
`event-driven` spec). The upsert SHALL set `status` to `ACTIVE` and populate
`cloud_id`, `updated_at`, and `site_url`; `site_url` SHALL be stored as NULL
when the proto field is blank. The consumer SHALL use a fixed consumer group so
each event is processed by exactly one replica. Delivery failures SHALL be
retried a bounded number of times before the message is routed to a dead-letter
topic (`tenant.tenant.installed.dlt`), unblocking the partition. The `meet`
service's `tenants` table baseline schema SHALL include a
`site_url VARCHAR(512)` column (nullable).

#### Scenario: Installed event persists site_url on new row

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  has no existing row in `meet`'s `tenants` table and the proto carries a
  non-blank `site_url`
- **THEN** a new row is inserted with `tenant_id` = cloudId, `status` =
  `ACTIVE`, and `site_url` set to the proto value

#### Scenario: Installed event persists site_url on upsert

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for an existing row
  (re-install scenario) and the proto carries a `site_url`
- **THEN** the existing row's `site_url` is updated alongside `status` and
  `updated_at`

#### Scenario: Blank site_url is stored as NULL

- **WHEN** the `TenantInstalled` proto's `site_url` field is empty or blank
- **THEN** the `meet` `tenants` row stores `NULL` in the `site_url` column and
  no error is raised

#### Scenario: Installed event creates a new tenant row without site_url

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  has no existing row and the proto carries no `site_url`
- **THEN** a new row is inserted with `status` = `ACTIVE` and `site_url` = NULL

#### Scenario: Installed event upserts an existing tenant row

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  already has a row in `meet`'s `tenants` table
- **THEN** the existing row is updated: `status` becomes `ACTIVE`, `updated_at`
  and `site_url` are refreshed; no duplicate row is created

#### Scenario: Malformed CloudEvent data is retried then dead-lettered

- **WHEN** a `tenant.tenant.installed` message cannot be decoded into a valid
  `TenantInstalled` proto (malformed JSON, missing required field)
- **THEN** the consumer retries up to the configured maximum attempts; if all
  retries fail the message is published to `tenant.tenant.installed.dlt` and the
  partition offset is committed so processing continues

#### Scenario: Transient DB failure is retried

- **WHEN** the upsert to `meet`'s `tenants` table fails with a transient
  database error
- **THEN** the consumer retries up to the configured maximum attempts before
  routing to the dead-letter topic

#### Scenario: Consumer group ensures single-replica processing

- **WHEN** multiple `meet` replicas are running
- **THEN** each `tenant.tenant.installed` message is processed by exactly one
  replica because all replicas share the same fixed consumer group id
