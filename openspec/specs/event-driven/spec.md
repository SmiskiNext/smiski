# event-driven Specification

## Purpose

TBD - created by archiving change add-tenant-install-app. Update Purpose after
archive.

## Requirements

### Requirement: Shared Protocol Buffers event contract

Tenant lifecycle events published to Kafka SHALL be defined by a shared Protocol
Buffers message under the proto module so that the publishing service and any
consuming service reference one authoritative schema. The installation event
SHALL be defined as a `TenantInstalled` message and the uninstallation event as
a `TenantUninstalled` message, both in package
`io.github.smiskinext.event.tenant.v1` (files
`io/github/smiskinext/event/tenant/v1/tenant_installed.proto` and
`tenant_uninstalled.proto`). Each message SHALL carry a **complete snapshot** of
the tenant aggregate at event time — cloudId, installation id, app id, app
version, environment id, site url, installer account id, status, installed
timestamp, updated timestamp, uninstalled timestamp, and purge-after timestamp —
so that a consumer can project the full tenant state from a single event without
a follow-up lookup. Fields that have no value at event time SHALL be encoded as
their proto3 default (empty string). New fields SHALL be added with new field
numbers so the change is backward-compatible for existing consumers. The protos
SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Publisher and consumer share one schema

- **WHEN** the tenant service publishes a lifecycle event and a consuming
  service reads it
- **THEN** both use the same generated message type from the shared proto
  module, with no divergent handwritten schema

#### Scenario: Installation event carries the full snapshot

- **WHEN** the tenant service publishes an installation event for an active
  tenant
- **THEN** the `TenantInstalled` message carries all twelve aggregate fields,
  with `status` set to the active status and the uninstalled/purge-after
  timestamps left at their proto3 default because they have no value yet

#### Scenario: Uninstallation event carries the full snapshot

- **WHEN** the tenant service publishes an uninstallation event
- **THEN** the `TenantUninstalled` message carries all twelve aggregate fields,
  including the installation metadata (app version, environment id, site url,
  installer account id) and the installed timestamp captured at install time

#### Scenario: Absent optional value uses proto3 default

- **WHEN** an aggregate field is null at event time (e.g. app version was never
  provided)
- **THEN** the corresponding proto field is the empty string rather than a
  distinct null representation

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `tenant_installed.proto` and `tenant_uninstalled.proto` pass the Buf
  `STANDARD` ruleset

#### Scenario: Added fields keep existing consumers working

- **WHEN** a consumer built against the previous schema reads an event produced
  with the extended schema
- **THEN** the consumer deserializes successfully because the new fields use new
  field numbers and are ignored by the older generated type

### Requirement: Transactional outbox enqueue

A domain lifecycle event SHALL be persisted to the `outbox_event` table within
the same database transaction as the aggregate state change, satisfying the
db-schema transactional-outbox requirement. The outbox row SHALL record the
`tenant_id`, `aggregate_id`, `aggregate_type`, `event_type`, `topic`, and a
`payload`. The `tenant_id` SHALL be populated by the Hibernate tenant
discriminator (`@TenantId`) on the outbox entity from the current request
tenant, so that the publisher does not read or pass the tenant explicitly. The
`outbox_event` table SHALL be treated as a global infrastructure table for
relaying: the background relay SHALL select rows across all tenants and SHALL
NOT be constrained by the request-bound tenant discriminator, even though each
row retains its `tenant_id` column and `(tenant_id, id)` primary key. The event
SHALL be durable if and only if the aggregate state change commits.

Every publishable domain event across all services SHALL implement one shared
event contract (`io.github.smiskinext.shared.domain.PublishableEvent`) whose
aggregate identity is exposed as a String `aggregateId()`; services SHALL NOT
define their own `PublishableEvent` interface. The domain SHALL remain free of
Protocol Buffers and messaging types; mapping the domain event to the proto
contract and to the stored payload SHALL happen at the infrastructure boundary
through a per-event-type mapper resolved by the domain event's runtime type.
Each mapper SHALL be typed to its concrete event type so that mapping is
compile-time-checked, and adding a new publishable event type SHALL require
registering a new mapper rather than modifying the publisher.

#### Scenario: Event enqueued atomically with the state change

- **WHEN** an aggregate change is recorded and its transaction commits
- **THEN** exactly one corresponding row exists in `outbox_event` with
  `published_at` null, `tenant_id` set to the current request tenant, and
  `aggregate_id` equal to the event's `aggregateId()`

#### Scenario: Rolled-back change leaves no event

- **WHEN** the aggregate transaction is rolled back before commit
- **THEN** no `outbox_event` row for that change exists

#### Scenario: Tenant id populated by the discriminator, not the publisher

- **WHEN** the shared publisher enqueues an event within a request that has a
  bound tenant
- **THEN** the stored row's `tenant_id` matches the request tenant even though
  the publisher never reads the tenant context or receives it as an argument

#### Scenario: Single shared event contract across services

- **WHEN** a service registers a publishable domain event
- **THEN** that event implements `shared.domain.PublishableEvent` directly, no
  service-specific `PublishableEvent` interface exists, and the shared publisher
  accepts it without a service-specific type

#### Scenario: Domain event mapped to proto at the boundary by a typed mapper

- **WHEN** the application registers a publishable domain event
- **THEN** the infrastructure layer resolves the concrete-typed mapper for that
  event type, maps it to its proto message, and produces the stored payload, and
  the domain event type itself imports no Protocol Buffers or Kafka types

#### Scenario: Unmapped event type is rejected without partial write

- **WHEN** a publishable event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

### Requirement: CloudEvent payload encoding

The stored outbox `payload` SHALL be a CloudEvents 1.0 event whose `data` is the
event's proto message encoded as proto-JSON, with `datacontenttype`
`application/json`. Encoding and decoding of the CloudEvent SHALL use a
structured CloudEvents JSON representation rather than hand-assembled JSON. The
CloudEvent `type` SHALL be a stable, versioned, reverse-DNS identifier for the
event, the CloudEvent `source` SHALL identify the producing service, the
CloudEvent `dataschema`, when present, SHALL be a valid URI reference, and the
CloudEvent `id` SHALL uniquely identify the event instance. Because
`outbox_event.payload` is a TEXT column, the encoding SHALL be a UTF-8 text
representation (no binary column change).

#### Scenario: Payload is a proto-JSON CloudEvent in TEXT

- **WHEN** an installation event is enqueued
- **THEN** the `payload` column holds a CloudEvent whose `data` is the proto
  message rendered as JSON and whose `datacontenttype` is `application/json`

#### Scenario: Event type is versioned and stable

- **WHEN** the CloudEvent for an installation is built
- **THEN** its `type` is the stable versioned installation identifier and its
  `id` is unique per event instance

#### Scenario: Malformed stored payload does not mark the row published

- **WHEN** the relay reads an outbox row whose stored payload cannot be decoded
  into a CloudEvent
- **THEN** the row is not published and not marked published; the failure is
  recorded and the row remains eligible for retry

### Requirement: Scheduled relay across tenants

An outbox relay SHALL run on a schedule and select unpublished outbox rows
across all tenants, independent of any request-bound tenant context, so that
rows written under real tenant identities are always visible to the background
relay. The relay SHALL claim a bounded batch of unpublished rows using
`FOR UPDATE SKIP LOCKED` semantics ordered by `created_at`, so that multiple
service replicas process disjoint batches without double-publishing and without
loading the entire backlog. The relay SHALL NOT hold a database transaction open
while performing transport (Kafka) network calls: it SHALL read the batch in a
short transaction, publish outside any transaction, and then persist results in
short transactions. Each row SHALL be published as a CloudEvent through the
configured transport, keyed by the aggregate id, and successfully published rows
SHALL be marked by setting `published_at`. A publish failure SHALL increment
`retry_count` and record `last_error` in an independent transaction while
leaving `published_at` null so the row is retried, per the db-schema
transactional-outbox requirement.

#### Scenario: Rows are relayed without a bound tenant context

- **WHEN** the relay runs on a background thread with no request tenant bound
  and finds an unpublished installation event stored under a real tenant
- **THEN** it selects that row, publishes the CloudEvent to the event's topic
  via the configured transport, and sets `published_at` on that row

#### Scenario: Concurrent replicas do not double-publish

- **WHEN** two relay instances run concurrently against the same unpublished
  rows
- **THEN** each unpublished row is claimed and published by at most one instance
  because rows are claimed with `FOR UPDATE SKIP LOCKED`

#### Scenario: Already-published rows are not resent

- **WHEN** the relay runs after an event has been published
- **THEN** that row (with `published_at` set) is not selected again and is not
  resent

#### Scenario: Publish failure is retryable and recorded independently

- **WHEN** publishing an outbox row to the transport fails
- **THEN** `retry_count` is incremented and `last_error` is recorded in a
  transaction that commits independently of the batch, `published_at` remains
  null, and the row is eligible for the next relay run

#### Scenario: Aggregate id is the transport message key

- **WHEN** an installation event is published
- **THEN** the message key is the tenant cloudId so all events for one tenant
  share a partition and ordering

### Requirement: Configurable, reusable outbox transport

The outbox publish path SHALL be provided by a transport abstraction so that the
concrete messaging technology is selected by configuration rather than
referenced directly by the relay. A Kafka transport SHALL be the default. All
relay tuning — including whether the relay is enabled, its polling delay, its
batch size, the CloudEvent source, and the selected transport — SHALL be
supplied through a single externalized, validated configuration properties type
rather than through scattered individual property injections. The generic outbox
machinery SHALL cover both the read/relay path and the write/enqueue path: a
storage port exposing an append operation for writing rows and
claim/mark/failure operations for relaying, a single reusable event publisher
over that storage port, CloudEvent encoding, a per-event mapper registry, the
transport port and Kafka adapter, and relay orchestration. A service SHALL be
able to publish and relay domain events by providing only a storage adapter and
its event mappers, without duplicating the publisher or relay logic.

#### Scenario: Transport is selected by configuration

- **WHEN** the outbox transport is configured to the Kafka transport (or left at
  its default)
- **THEN** the relay publishes through the Kafka transport, and the relay code
  depends only on the transport abstraction, not on a specific messaging client

#### Scenario: Relay tuning comes from externalized configuration

- **WHEN** the relay polling delay, batch size, CloudEvent source, or enabled
  flag is changed in external configuration
- **THEN** the relay behavior reflects the configured values without code
  changes

#### Scenario: Write path is reused across services

- **WHEN** a service provides only an outbox storage adapter and its event
  mappers
- **THEN** it enqueues events through the single shared publisher over the
  shared storage port, without declaring its own event-publisher port or write
  adapter

#### Scenario: Relay stays inactive when no storage adapter is present

- **WHEN** a service includes the shared outbox machinery but provides no outbox
  storage adapter
- **THEN** the relay does not activate and the service starts normally
