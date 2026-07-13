## ADDED Requirements

### Requirement: Shared Protocol Buffers event contract

Tenant lifecycle events published to Kafka SHALL be defined by a shared Protocol
Buffers message under the proto module so that the publishing service and any
consuming service reference one authoritative schema. The installation event
SHALL be defined as a `TenantInstalled` message in package
`io.github.smiskinext.event.tenant.v1` (file
`io/github/smiskinext/event/tenant/v1/tenant_installed.proto`). The message
SHALL carry the fields needed to project a tenant: cloudId, installation id, app
id, and the installation metadata recorded by the tenant service. The proto
SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Publisher and consumer share one schema

- **WHEN** the tenant service publishes an installation event and a consuming
  service reads it
- **THEN** both use the same generated `TenantInstalled` message type from the
  shared proto module, with no divergent handwritten schema

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `tenant_installed.proto` passes the Buf `STANDARD` ruleset

### Requirement: Transactional outbox enqueue

A tenant lifecycle event SHALL be persisted to the `outbox_event` table within
the same database transaction as the tenant upsert, satisfying the db-schema
transactional-outbox requirement. The outbox row SHALL record the `aggregate_id`
(the cloudId), `aggregate_type`, `event_type`, `topic`, and a `payload`. The
event SHALL be durable if and only if the tenant state change commits. The
domain SHALL remain free of Protocol Buffers and messaging types; mapping the
domain event to the proto contract and to the stored payload SHALL happen at the
infrastructure boundary.

#### Scenario: Event enqueued atomically with the upsert

- **WHEN** an installation is recorded and its transaction commits
- **THEN** exactly one corresponding row exists in `outbox_event` with
  `published_at` null, `aggregate_id` equal to the cloudId, and the tenant row
  is present

#### Scenario: Rolled-back upsert leaves no event

- **WHEN** the tenant upsert transaction is rolled back before commit
- **THEN** no `outbox_event` row for that installation exists

#### Scenario: Domain event mapped to proto at the boundary

- **WHEN** the application registers the tenant installation domain event
- **THEN** the infrastructure layer maps it to the `TenantInstalled` proto
  message and produces the stored payload, and the domain event type itself
  imports no Protocol Buffers or Kafka types

### Requirement: CloudEvent payload encoding

The stored outbox `payload` SHALL be a CloudEvents 1.0 event whose `data` is the
`TenantInstalled` proto message encoded as proto-JSON, with `datacontenttype`
`application/json`. The CloudEvent `type` SHALL be a stable, versioned,
reverse-DNS identifier for the installation event, and the CloudEvent `id` SHALL
uniquely identify the event instance. Because `outbox_event.payload` is a TEXT
column, the encoding SHALL be a UTF-8 text representation (no binary column
change).

#### Scenario: Payload is a proto-JSON CloudEvent in TEXT

- **WHEN** an installation event is enqueued
- **THEN** the `payload` column holds a CloudEvent whose `data` is the proto
  message rendered as JSON and whose `datacontenttype` is `application/json`

#### Scenario: Event type is versioned and stable

- **WHEN** the CloudEvent for an installation is built
- **THEN** its `type` is the stable versioned installation identifier and its
  `id` is unique per event instance

### Requirement: Scheduled Kafka relay

An outbox relay SHALL run on a schedule, select only unpublished outbox rows,
publish each to Kafka as a CloudEvent using the configured
`CloudEventSerializer` keyed by the aggregate id, and mark successfully
published rows by setting `published_at`. A publish failure SHALL increment
`retry_count` and record `last_error` while leaving `published_at` null so the
row is retried, per the db-schema transactional-outbox requirement.

#### Scenario: Unpublished rows are relayed and marked

- **WHEN** the relay runs and finds an unpublished installation event
- **THEN** it publishes the CloudEvent to the event's topic on Kafka and sets
  `published_at` on that row

#### Scenario: Already-published rows are not resent

- **WHEN** the relay runs after an event has been published
- **THEN** that row (with `published_at` set) is not selected again and is not
  resent

#### Scenario: Publish failure is retryable

- **WHEN** publishing an outbox row to Kafka fails
- **THEN** `retry_count` is incremented, `last_error` is recorded,
  `published_at` remains null, and the row is eligible for the next relay run

#### Scenario: Aggregate id is the Kafka message key

- **WHEN** an installation event is published to Kafka
- **THEN** the message key is the tenant cloudId so all events for one tenant
  share a partition and ordering
