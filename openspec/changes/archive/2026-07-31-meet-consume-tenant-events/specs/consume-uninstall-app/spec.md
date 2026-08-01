## ADDED Requirements

### Requirement: Consume tenant-uninstalled event and upsert local projection

The `meet` service SHALL consume CloudEvents from the Kafka topic
`tenant.tenant.uninstalled` and upsert a row into its local `tenants` projection
table. Each consumed event SHALL be decoded from CloudEvents 1.0 structured JSON
whose `data` is a `TenantUninstalled` proto message rendered as proto-JSON (per
`event-driven` spec). The upsert SHALL set `status` to `UNINSTALLED` and
populate `uninstalled_at` and `purge_after` from the proto's `uninstalled_at`
and `purge_after` fields (ISO-8601 strings); `updated_at` SHALL be refreshed.
The consumer SHALL use a fixed consumer group so each event is processed by
exactly one replica. Delivery failures SHALL be retried a bounded number of
times before the message is routed to a dead-letter topic
(`tenant.tenant.uninstalled.dlt`), unblocking the partition.

#### Scenario: Uninstalled event marks an existing tenant row

- **WHEN** a `tenant.tenant.uninstalled` CloudEvent arrives for a `cloudId` that
  has an existing `ACTIVE` row in `meet`'s `tenants` table
- **THEN** the row's `status` becomes `UNINSTALLED`, `uninstalled_at` and
  `purge_after` are set from the event data, and `updated_at` is refreshed

#### Scenario: Uninstalled event upserts even when row is absent

- **WHEN** a `tenant.tenant.uninstalled` CloudEvent arrives for a `cloudId` that
  has no existing row in `meet`'s `tenants` table (e.g. consumer replayed out of
  order)
- **THEN** a new row is inserted with `status` = `UNINSTALLED`,
  `uninstalled_at`, and `purge_after` populated from the event

#### Scenario: Redelivered uninstall event is idempotent

- **WHEN** a `tenant.tenant.uninstalled` CloudEvent for a `cloudId` is delivered
  more than once (Kafka at-least-once redelivery)
- **THEN** each delivery produces the same upsert result; no duplicate row is
  created and `uninstalled_at` / `purge_after` are not changed from the first
  delivery's values (subsequent upserts overwrite with identical data)

#### Scenario: Malformed CloudEvent data is retried then dead-lettered

- **WHEN** a `tenant.tenant.uninstalled` message cannot be decoded into a valid
  `TenantUninstalled` proto (malformed JSON, missing required field)
- **THEN** the consumer retries up to the configured maximum attempts; if all
  retries fail the message is published to `tenant.tenant.uninstalled.dlt` and
  the partition offset is committed so processing continues

#### Scenario: Consumer group ensures single-replica processing

- **WHEN** multiple `meet` replicas are running
- **THEN** each `tenant.tenant.uninstalled` message is processed by exactly one
  replica because all replicas share the same fixed consumer group id
