# livekit-webhook Specification

## Purpose

Handles LiveKit server webhooks for the meet service — receiving room and
participant lifecycle events, verifying their authenticity, and driving meeting
status transitions and participation log management asynchronously.

## Requirements

### Requirement: LiveKit webhook receiver endpoint

The meet service SHALL expose an unauthenticated-at-gateway HTTP `POST` endpoint
that receives LiveKit server webhooks with `Content-Type`
`application/webhook+json`. The endpoint SHALL be served under the versioned
integer API scheme (`/api/{version}/...`) in accordance with the api-convention
capability and SHALL bypass the tenant header filter, since LiveKit sends no
tenant header. The endpoint SHALL read the raw request body and the
`Authorization` header and SHALL validate them using the LiveKit server SDK
webhook receiver keyed by the configured LiveKit API key and secret. On a valid
signature the endpoint SHALL acknowledge with `200 OK`. On an invalid or missing
signature the endpoint SHALL respond `401` and SHALL NOT perform any state
change.

#### Scenario: Valid signed webhook is accepted

- **WHEN** LiveKit posts a webhook whose `Authorization` JWT correctly signs the
  raw body using the configured API key/secret
- **THEN** the endpoint responds `200 OK` and the event is accepted for
  processing

#### Scenario: Invalid signature is rejected

- **WHEN** a request arrives with a missing or invalid `Authorization` token, or
  a body whose hash does not match the token
- **THEN** the endpoint responds `401` and no meeting or participation-log state
  is changed

#### Scenario: Malformed body is rejected without side effects

- **WHEN** a request carries a body that cannot be decoded into a webhook event
- **THEN** the endpoint responds with a client error and no meeting or
  participation-log state is changed

### Requirement: Fast acknowledgement and asynchronous processing

The endpoint SHALL decouple signature verification from event processing: after
verifying the signature it SHALL enqueue the decoded event to an internal
messaging channel keyed by the room name and SHALL return `200 OK` without
performing the database work inline. Event processing SHALL be performed by a
consumer of that channel. Enqueuing events keyed by room name SHALL preserve
per-room ordering of delivered events.

#### Scenario: Verified event is enqueued and acknowledged immediately

- **WHEN** a valid webhook is received
- **THEN** the decoded event is enqueued to the internal channel keyed by its
  room name and the endpoint returns `200 OK` before the database work runs

#### Scenario: Events for one room are processed in order

- **WHEN** multiple events for the same room are enqueued in sequence
- **THEN** the consumer processes them in the order they were enqueued for that
  room

### Requirement: Tenant resolution from room metadata

Because webhooks carry no tenant header, the consumer SHALL resolve the owning
tenant from the LiveKit room metadata present in the webhook payload, which the
token-issuing flows populate with the tenant identifier. The consumer SHALL bind
that tenant into the request-scoped tenant context for the duration of its
processing transaction and SHALL clear it afterward, so all reads and writes are
scoped to the correct tenant. When the room metadata does not carry a resolvable
tenant identifier, the consumer SHALL NOT perform tenant-scoped writes for that
event and SHALL record the anomaly.

#### Scenario: Tenant bound from room metadata

- **WHEN** the consumer processes an event whose room metadata carries a tenant
  identifier
- **THEN** the tenant context is bound to that identifier for the processing
  transaction and cleared afterward, and all persisted rows belong to that
  tenant

#### Scenario: Missing tenant metadata is not silently misattributed

- **WHEN** the consumer processes an event whose room metadata has no resolvable
  tenant identifier
- **THEN** no tenant-scoped write is performed for that event and the anomaly is
  recorded

### Requirement: Meeting lifecycle transitions from room events

The consumer SHALL transition meeting status from room-lifecycle webhooks,
resolving the meeting identifier from the room name of the form
`meeting-<meetingId>`. On a `room_started` event it SHALL transition the meeting
`SCHEDULED → RUNNING`. On a `room_finished` event it SHALL transition the
meeting `RUNNING → COMPLETED` and close every still-active participation log for
that meeting. Both transitions SHALL be idempotent against current state: an
event that does not correspond to a legal transition (already RUNNING, already
COMPLETED, or unknown meeting) SHALL be acknowledged without error and without
changing state. The corresponding meeting domain events SHALL be enqueued to the
transactional outbox only when a real transition occurs.

#### Scenario: room_started starts a scheduled meeting

- **WHEN** a `room_started` event is processed for a meeting currently in
  `SCHEDULED`
- **THEN** the meeting transitions to `RUNNING` and a meeting-started event is
  enqueued to the outbox

#### Scenario: room_started on an already running meeting is a no-op

- **WHEN** a `room_started` event is processed for a meeting already in
  `RUNNING` or `COMPLETED` (e.g. an instant meeting created LIVE, or a retry)
- **THEN** the meeting status is unchanged, no duplicate meeting-started event
  is enqueued, and the event is acknowledged without error

#### Scenario: room_finished completes a running meeting and closes logs

- **WHEN** a `room_finished` event is processed for a meeting in `RUNNING` with
  active participation logs
- **THEN** the meeting transitions to `COMPLETED`, every still-active
  participation log for the meeting is closed with close reason `LEFT` and a
  left-at timestamp of the event time, and a meeting-completed event is enqueued

#### Scenario: room_finished on an already completed meeting is a no-op

- **WHEN** a `room_finished` event is processed for a meeting already in
  `COMPLETED` (retry) or for an unknown meeting
- **THEN** no state changes and the event is acknowledged without error

### Requirement: Participation log lifecycle from participant events

The consumer SHALL maintain the append-only participation log from participant
webhooks. On a `participant_joined` event it SHALL create a participation log
for the joining account and device — deriving the account identifier and device
from the LiveKit participant identity of the form `<accountId>:<deviceId>` and
the role from the participant attributes — and SHALL assign the LiveKit
participant session id (SID) from the event. Before inserting, if an orphaned
active session exists for the same meeting and identity (from a lost prior leave
webhook), the consumer SHALL supersede that active session per the
participation-log rejoin contract. On a `participant_left` event it SHALL close
the active participation log matching the event's participant SID by setting its
left-at timestamp and close reason `LEFT`. All participant handling SHALL be
idempotent: a `participant_joined` whose SID is already recorded SHALL NOT
create a duplicate log, and a `participant_left` for an already-closed or
unknown session SHALL be acknowledged without error. When a real join or leave
occurs, the corresponding participant domain event SHALL be enqueued to the
transactional outbox.

#### Scenario: participant_joined creates a log and assigns the SID

- **WHEN** a `participant_joined` event is processed for an identity with no
  existing active session for the meeting
- **THEN** a new participation log is created for the account and device with
  the event's participant SID assigned and the role taken from participant
  attributes, and a participant-joined event is enqueued to the outbox

#### Scenario: Rejoin supersedes an orphaned active session

- **WHEN** a `participant_joined` event is processed for an identity that still
  has an active (unclosed) session for the meeting from a lost prior leave
- **THEN** the orphaned session is closed with reason `SUPERSEDED` before the
  new session is inserted, so at most one active session per identity remains

#### Scenario: Duplicate participant_joined is idempotent

- **WHEN** a `participant_joined` event is redelivered for a SID that is already
  recorded on an active log
- **THEN** no duplicate participation log is created and the event is
  acknowledged without error

#### Scenario: participant_left closes the matching active session

- **WHEN** a `participant_left` event is processed whose SID matches an active
  participation log
- **THEN** that log is closed with a left-at timestamp of the event time and
  close reason `LEFT`, and a participant-left event is enqueued to the outbox

#### Scenario: participant_left for an already-closed session is idempotent

- **WHEN** a `participant_left` event is redelivered for a SID whose log is
  already closed, or references an unknown SID
- **THEN** no state changes and the event is acknowledged without error

### Requirement: Non-handled events are acknowledged as no-ops

The consumer SHALL acknowledge webhook events outside the four handled types —
including `track_published`, `track_unpublished`,
`participant_connection_aborted`, and all `egress_*` and `ingress_*` events —
without changing meeting or participation-log state. Recording/egress concerns
are owned by the record service and SHALL NOT be handled here.

#### Scenario: Track and egress events do not change state

- **WHEN** a `track_published`, `track_unpublished`, `egress_started`, or
  `ingress_started` event is processed
- **THEN** no meeting or participation-log state is changed and the event is
  acknowledged without error

#### Scenario: participant_connection_aborted is a no-op

- **WHEN** a `participant_connection_aborted` event is processed
- **THEN** no participation-log state is changed and the event is acknowledged
  without error
