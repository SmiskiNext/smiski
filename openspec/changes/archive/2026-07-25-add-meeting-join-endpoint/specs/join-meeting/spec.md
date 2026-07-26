## ADDED Requirements

### Requirement: Join a meeting endpoint

The meet service SHALL expose `POST /meetings/{id}:join` allowing an
authenticated account to join a meeting. The request body SHALL carry a
non-blank `displayName` (max 100 characters) and a non-blank `deviceId`, and MAY
carry an optional `avatarUrl`. The account identifier SHALL be taken from the
request account context and the tenant from the tenant context; neither SHALL be
accepted in the body. A successful response SHALL be `200 OK` with a body
carrying a `status` field that distinguishes the outcome. Failures SHALL be
returned as RFC 9457 `application/problem+json`.

#### Scenario: Missing required field is a validation error

- **WHEN** a client calls `POST /meetings/{id}:join` with a blank `displayName`
  or blank `deviceId`
- **THEN** the response is `400` `application/problem+json` with code
  `VALIDATION_ERROR` and a `REQUIRED` entry for the offending field

#### Scenario: Unknown meeting

- **WHEN** a client joins a meeting id that does not exist for the current
  tenant
- **THEN** the response is `404` `application/problem+json` with a
  machine-readable meeting-not-found code

### Requirement: Immediate join under ALLOW_ALL admission

When the target meeting's admission policy is `ALLOW_ALL`, the join operation
SHALL admit the caller immediately: it SHALL generate a LiveKit access token for
the caller as a `PARTICIPANT` whose participant attributes carry the caller's
`role` and, when supplied, `avatarUrl`, and return `200 OK` with `status`
`APPROVED`, a generated correlation `requestId`, the LiveKit token, and the room
name. The join operation SHALL NOT record a participation log; recording the
participation session is deferred to the `participant_joined` webhook (the
source of truth), which prevents orphan logs for callers who obtain a token but
never connect. Capacity SHALL be enforced so that the number of active
participants never exceeds the meeting's `maxParticipants`, evaluated so that
concurrent joins cannot exceed the limit.

#### Scenario: Open meeting admits the caller

- **WHEN** a caller joins an `ALLOW_ALL` meeting that has capacity
- **THEN** the response is `200` with `status` `APPROVED`, a generated
  `requestId`, a non-empty LiveKit token whose participant attributes carry
  `role` and (when supplied) `avatarUrl`, and the room name; no participation
  log is recorded by the join operation itself

#### Scenario: Meeting at capacity is rejected

- **WHEN** a caller joins an `ALLOW_ALL` meeting whose active participants
  already equal `maxParticipants`
- **THEN** the response is `409` `application/problem+json` with a meeting-full
  code and no new participation log is created

#### Scenario: Concurrent joins do not exceed capacity

- **WHEN** multiple callers join simultaneously such that only one seat remains
- **THEN** at most one additional caller is admitted and the others receive the
  meeting-full error, so the active participant count never exceeds
  `maxParticipants`

### Requirement: Pending join request under MANUAL_APPROVAL admission

When the target meeting's admission policy is `MANUAL_APPROVAL`, the join
operation SHALL create a pending join request stored in Redis with a
time-to-live, SHALL publish a join-created event for host notification, and
SHALL return `200 OK` with `status` `PENDING` and the generated `requestId`,
without issuing a LiveKit token. A repeated join from the same device for the
same meeting while a pending request exists SHALL be idempotent and return the
existing request rather than creating a duplicate.

#### Scenario: Manual-approval meeting creates a pending request

- **WHEN** a caller joins a `MANUAL_APPROVAL` meeting
- **THEN** the response is `200` with `status` `PENDING` and a `requestId`, no
  LiveKit token is returned, and a pending join request exists in Redis with a
  positive remaining TTL

#### Scenario: Join-created event is published for the host

- **WHEN** a pending join request is created
- **THEN** a `meet.join.created` event carrying the meeting id, request id,
  account id, display name, device id, and (when supplied) avatar url is
  enqueued to the outbox in the same transaction as the request creation

#### Scenario: Duplicate device request is idempotent

- **WHEN** the same device submits a second join for a meeting that already has
  its pending request
- **THEN** the response returns the existing pending request id rather than
  creating a second request

#### Scenario: Pending request expires after its TTL

- **WHEN** a pending join request's TTL elapses without host action
- **THEN** the request is no longer retrievable from Redis and no longer appears
  in the meeting's pending queue

### Requirement: Redis-backed join request storage

Join requests SHALL be stored only in Redis, never in the relational database.
The store SHALL maintain, per meeting, a queue of pending request ids ordered by
expiry, the full request metadata per request, and a device-to-request index for
duplicate detection. All multi-key mutations (create, remove, status update)
SHALL be atomic so the queue, metadata, and device index never diverge.
Serialization SHALL use the Jackson JSON serializer supported by the service's
Redis version.

#### Scenario: Pending queue reflects created requests

- **WHEN** two pending requests exist for a meeting
- **THEN** querying pending requests for that meeting returns both, ordered by
  requested time ascending

#### Scenario: Atomic removal clears all keys

- **WHEN** a join request is removed from the queue
- **THEN** its queue entry, metadata, and device index entry are all deleted
  together, leaving no orphaned key
