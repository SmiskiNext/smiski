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

Publishable domain events registered on an aggregate SHALL be drained through a
single shared publisher operation invoked with the aggregate, rather than by
each application service iterating the aggregate's events. The drain operation
SHALL dispatch each registered domain event to the framework application-event
mechanism and clear the aggregate's events. A single shared pre-commit
transactional listener SHALL receive events that are publishable and enqueue
each into the outbox within the ongoing transaction, so the enqueue remains
atomic with the aggregate state change; events that are not publishable SHALL be
ignored by that listener. Application services SHALL NOT filter events by type
or clear the aggregate's events directly.

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

#### Scenario: Aggregate events drained by a single publisher call

- **WHEN** an application service records aggregate changes and invokes the
  shared publisher with the aggregate
- **THEN** each registered publishable event is enqueued into the outbox and the
  aggregate's events are cleared, without the service iterating or filtering
  events itself

#### Scenario: Non-publishable domain event is not enqueued

- **WHEN** an aggregate registers a domain event that is not publishable and the
  shared publisher is invoked with the aggregate
- **THEN** no `outbox_event` row is written for that event and the pre-commit
  listener ignores it

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
over that storage port, a shared aggregate-draining event publisher over the
framework application-event mechanism, a shared pre-commit listener that
enqueues publishable events, CloudEvent encoding, a per-event mapper registry,
the transport port and Kafka adapter, and relay orchestration. A service SHALL
be able to publish and relay domain events by providing only a storage adapter
and its event mappers, without duplicating the publisher, the event-draining
dispatch, the enqueue listener, or the relay logic.

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
  shared storage port, draining aggregates via the shared dispatch and enqueue
  listener, without declaring its own event-publisher port, dispatch loop, or
  write adapter

#### Scenario: Relay stays inactive when no storage adapter is present

- **WHEN** a service includes the shared outbox machinery but provides no outbox
  storage adapter
- **THEN** the relay does not activate, the shared dispatch and enqueue beans do
  not activate, and the service starts normally

### Requirement: Meeting join-created event contract

The meet service SHALL publish a join-created event when a pending join request
is created under `MANUAL_APPROVAL` admission. The event SHALL be defined by a
shared Protocol Buffers message `JoinCreated` in package
`io.github.smiskinext.event.meet.v1` (file
`io/github/smiskinext/event/meet/v1/join_created.proto`), carrying the meeting
id, join request id, tenant id, account id, display name, device id, occurrence
time, and avatar url. Fields with no value at event time SHALL use their proto3
default (empty string); a requester with no avatar url SHALL be represented by
an empty `avatar_url`. The event SHALL be published to Kafka topic
`meet.join.created` with CloudEvent type
`io.github.smiskinext.meet.join.created.v1`, enqueued through the existing
transactional outbox and rendered as a CloudEvents 1.0 structured-JSON payload
whose `data` is the proto message as proto-JSON. Publishing SHALL go through a
registered per-event proto mapper; an unmapped event SHALL fail enqueue without
a partial write. The proto SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Join-created event published on pending request creation

- **WHEN** a pending join request is created under `MANUAL_APPROVAL`
- **THEN** exactly one `meet.join.created` outbox row is enqueued in the same
  transaction, keyed by the meeting id, with CloudEvent type
  `io.github.smiskinext.meet.join.created.v1`

#### Scenario: Event data carries the join fields

- **WHEN** the join-created CloudEvent is built
- **THEN** its `data` is the `JoinCreated` proto rendered as JSON, carrying the
  meeting id, join request id, account id, display name, device id, and avatar
  url, with `datacontenttype` `application/json`

#### Scenario: Missing proto mapper fails without partial write

- **WHEN** the join-created event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `join_created.proto` passes the Buf `STANDARD` ruleset

### Requirement: Meeting join-decision event contract

The meet service SHALL publish a join-approved event when a host accepts a
pending join request and a join-denied event when a host declines one. The
approved event SHALL be defined by a shared Protocol Buffers message
`JoinApproved` in package `io.github.smiskinext.event.meet.v1` (file
`io/github/smiskinext/event/meet/v1/join_approved.proto`), carrying the meeting
id, join request id, tenant id, account id, device id, LiveKit token, room name,
approving account id, and occurrence time. The denied event SHALL be defined by
`JoinDenied` (file `io/github/smiskinext/event/meet/v1/join_denied.proto`),
carrying the meeting id, join request id, tenant id, account id, device id,
denying account id, and occurrence time. Fields with no value at event time
SHALL use their proto3 default (empty string).

The approved event SHALL be published to Kafka topic `meet.join.approved` with
CloudEvent type `io.github.smiskinext.meet.join.approved.v1`, and the denied
event to topic `meet.join.denied` with CloudEvent type
`io.github.smiskinext.meet.join.denied.v1`. Both SHALL be enqueued through the
existing transactional outbox in the same transaction as the join request state
change, rendered as a CloudEvents 1.0 structured-JSON payload whose `data` is
the proto message as proto-JSON, and published through a registered per-event
proto mapper; an unmapped event SHALL fail enqueue without a partial write. Both
events SHALL be marked as SSE-triggering so their outbox rows are relayed
immediately after commit. Both protos SHALL satisfy the module's Buf `STANDARD`
lint rules.

#### Scenario: Join-approved event published on accept

- **WHEN** a host accepts a pending join request
- **THEN** exactly one `meet.join.approved` outbox row is enqueued in the same
  transaction, keyed by the meeting id, with CloudEvent type
  `io.github.smiskinext.meet.join.approved.v1`, whose `data` carries the join
  request id, account id, device id, LiveKit token, and room name

#### Scenario: Join-denied event published on decline

- **WHEN** a host declines a pending join request
- **THEN** exactly one `meet.join.denied` outbox row is enqueued in the same
  transaction, keyed by the meeting id, with CloudEvent type
  `io.github.smiskinext.meet.join.denied.v1`, whose `data` carries the join
  request id and account id

#### Scenario: Missing proto mapper fails without partial write

- **WHEN** a join-approved or join-denied event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

#### Scenario: Decision events relayed immediately after commit

- **WHEN** a join-approved or join-denied outbox row is committed
- **THEN** the post-commit relay publishes it without waiting for the scheduled
  outbox poll, because the event is marked SSE-triggering

#### Scenario: Protos pass standard lint

- **WHEN** the proto module is linted
- **THEN** `join_approved.proto` and `join_denied.proto` pass the Buf `STANDARD`
  ruleset

### Requirement: Meeting invitee-response event contract

The meet service SHALL publish an invitee-response event when an invitee
accepts, declines, or tentatively responds to their invitation. The events SHALL
be defined by shared Protocol Buffers messages in package
`io.github.smiskinext.event.meet.v1` (file
`io/github/smiskinext/event/meet/v1/invitee_response.proto`): `InviteeAccepted`,
`InviteeDeclined`, and `InviteeTentative`. Each message SHALL carry the event
id, tenant id, meeting id, inviter id, invitee id, invitee email, resolved
status, and response timestamp, together with the meeting context required to
build an iCalendar reply without a follow-up lookup: meeting title, start time,
end time, timezone, organizer email, organizer display name, invitee display
name, calendar UID, and calendar sequence. Fields with no value at event time
SHALL use their proto3 default (empty string). New fields SHALL be added with
new field numbers so the change is backward compatible for existing consumers.

The accepted event SHALL be published to Kafka topic `meet.invitee.accepted`
with CloudEvent type `io.github.smiskinext.meet.invitee.accepted.v1`, the
declined event to `meet.invitee.declined` with type
`io.github.smiskinext.meet.invitee.declined.v1`, and the tentative event to
`meet.invitee.tentative` with type
`io.github.smiskinext.meet.invitee.tentative.v1`. Each SHALL be enqueued through
the existing transactional outbox in the same transaction as the invitee state
change, rendered as a CloudEvents 1.0 structured-JSON payload whose `data` is
the proto message as proto-JSON, and published through a registered per-event
proto mapper; an unmapped event SHALL fail enqueue without a partial write. The
protos SHALL satisfy the module's Buf `STANDARD` lint rules.

#### Scenario: Accepted event published on accept

- **WHEN** an invitee accepts their invitation and the transaction commits
- **THEN** exactly one `meet.invitee.accepted` outbox row is enqueued in the
  same transaction, keyed by the invitee aggregate id, with CloudEvent type
  `io.github.smiskinext.meet.invitee.accepted.v1`

#### Scenario: Tentative event published on tentative response

- **WHEN** an invitee marks their invitation tentative and the transaction
  commits
- **THEN** exactly one `meet.invitee.tentative` outbox row is enqueued in the
  same transaction with CloudEvent type
  `io.github.smiskinext.meet.invitee.tentative.v1`

#### Scenario: Response event data carries the meeting context

- **WHEN** an invitee-response CloudEvent is built
- **THEN** its `data` carries the invitee email, status, and response timestamp
  together with the meeting title, start time, end time, timezone, organizer
  email and display name, calendar UID, and calendar sequence, with
  `datacontenttype` `application/json`

#### Scenario: Missing proto mapper fails without partial write

- **WHEN** an invitee-response event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

#### Scenario: Added fields keep existing consumers working

- **WHEN** a consumer built against the previous accepted/declined schema reads
  an event produced with the extended schema
- **THEN** the consumer deserializes successfully because the added
  meeting-context fields use new field numbers

#### Scenario: Proto passes standard lint

- **WHEN** the proto module is linted
- **THEN** `invitee_response.proto` passes the Buf `STANDARD` ruleset

### Requirement: MeetingInvitationsCreated event carries issue link fields

The `meet.meeting.invitations.created` CloudEvent payload SHALL include the
meeting's Jira issue identifier fields so the `notification` service can
construct a Jira deep-link in invitation emails. The `MeetingInvitationsCreated`
proto message SHALL carry `issue_id` (field 14), `issue_key` (field 15), and
`project_key` (field 16) as string fields. The `meet` service's
`MeetingInvitationsCreatedEventProtoMapper` SHALL populate these fields from the
meeting aggregate's issue link. The `MeetingInvitationsCreatedEvent` domain
record SHALL carry the corresponding `issueId`, `issueKey`, and `projectKey`
fields.

#### Scenario: Invitation event includes issue_key for deep-link construction

- **WHEN** the `meet` service publishes a `meet.meeting.invitations.created`
  event for a meeting linked to a Jira issue
- **THEN** the event payload contains non-blank `issue_id`, `issue_key`, and
  `project_key` values matching the meeting's stored issue link

#### Scenario: Existing consumers unaffected by new fields

- **WHEN** a consumer that predates this change reads a
  `MeetingInvitationsCreated` message
- **THEN** it continues to function correctly because the new fields are
  append-only with proto3 empty-string defaults

### Requirement: MeetingInfoUpdated event carries short_code

The `meet.meeting.info.updated` CloudEvent payload SHALL include the meeting's
`short_code` so the `notification` service can display it in update emails. The
`MeetingInfoUpdated` proto message SHALL carry `short_code` as a top-level
string field (field 10). The `meet` service's
`MeetingInfoUpdatedEventProtoMapper` SHALL populate `short_code` from the
meeting aggregate. The `MeetingInfoUpdatedEvent` domain record SHALL carry a
`shortCode` field.

#### Scenario: Update event includes short_code for email display

- **WHEN** the `meet` service publishes a `meet.meeting.info.updated` event
- **THEN** the event payload contains a non-blank `short_code` matching the
  meeting's stored short code

### Requirement: Invitee response events carry issue link and short_code

The invitee response proto messages (`InviteeAccepted`, `InviteeDeclined`,
`InviteeTentative`) SHALL each carry `issue_id` (field 18), `issue_key` (field
19), `project_key` (field 20), and `short_code` (field 21) as string fields. The
`meet` service's corresponding proto mappers SHALL populate these fields. The
domain event records SHALL carry the corresponding fields.

#### Scenario: Accepted response event includes issue and short_code fields

- **WHEN** the `meet` service publishes a `meet.invitee.accepted` event
- **THEN** the payload contains `issue_id`, `issue_key`, `project_key`, and
  `short_code` matching the meeting's stored values

#### Scenario: Declined and tentative response events also carry issue fields

- **WHEN** the `meet` service publishes `meet.invitee.declined` or
  `meet.invitee.tentative` events
- **THEN** each payload contains the same `issue_id`, `issue_key`,
  `project_key`, and `short_code` fields

#### Scenario: Existing consumers unaffected by new fields

- **WHEN** a consumer that predates this change reads an invitee response
  message
- **THEN** it continues to function correctly because the new fields are
  append-only with proto3 empty-string defaults
