## ADDED Requirements

### Requirement: Consume tenant-installed event and upsert local projection

The `meet` service SHALL consume CloudEvents from the Kafka topic
`tenant.tenant.installed` and upsert a row into its local `tenants` projection
table. Each consumed event SHALL be decoded from CloudEvents 1.0 structured JSON
whose `data` is a `TenantInstalled` proto message rendered as proto-JSON (per
`event-driven` spec). The upsert SHALL set `status` to `ACTIVE` and populate
`cloud_id`, `updated_at`; fields absent in the proto (null or empty-string
default) SHALL be stored as their corresponding SQL null or empty value. The
consumer SHALL use a fixed consumer group so each event is processed by exactly
one replica. Delivery failures SHALL be retried a bounded number of times before
the message is routed to a dead-letter topic (`tenant.tenant.installed.dlt`),
unblocking the partition.

#### Scenario: Installed event creates a new tenant row

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  has no existing row in `meet`'s `tenants` table
- **THEN** a new row is inserted with `tenant_id` = cloudId and `status` =
  `ACTIVE`

#### Scenario: Installed event upserts an existing tenant row

- **WHEN** a `tenant.tenant.installed` CloudEvent arrives for a `cloudId` that
  already has a row in `meet`'s `tenants` table (e.g. re-install after
  uninstall)
- **THEN** the existing row is updated: `status` becomes `ACTIVE` and
  `updated_at` is refreshed; no duplicate row is created

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
