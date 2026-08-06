# join-meeting Specification

## Purpose

TBD - created by archiving change add-meeting-join-endpoint. Update Purpose
after archive.

## Requirements

### Requirement: Join a meeting endpoint

The meet service SHALL expose `POST /meetings/{id}:join` allowing an
authenticated account to join a meeting. The endpoint SHALL require the
`view-meeting` project permission — if the caller's permission context does not
contain `view-meeting`, the endpoint SHALL reject the request with a `403`
Problem Details response and code `NOT_AUTHORIZED` before executing the use
case. The request body SHALL carry a non-blank `displayName` (max 100
characters) and a non-blank `deviceId`, and MAY carry an optional `avatarUrl`.
The account identifier SHALL be taken from the request account context and the
tenant from the tenant context; neither SHALL be accepted in the body. A
successful response SHALL be `200 OK` with a body carrying a `status` field that
distinguishes the outcome. Failures SHALL be returned as RFC 9457 Problem
Details.

#### Scenario: Missing required field is a validation error

- **WHEN** a client calls `POST /meetings/{id}:join` with a blank `displayName`
  or blank `deviceId`
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and a `REQUIRED` entry for the offending field

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends `POST /meetings/{id}:join`
- **THEN** the response is `403` Problem Details with code `NOT_AUTHORIZED` and
  no join operation is executed

#### Scenario: Unknown meeting

- **WHEN** a client joins a meeting id that does not exist for the current
  tenant
- **THEN** the response is `404` Problem Details with a machine-readable
  meeting-not-found code

### Requirement: Immediate join under ALLOW_ALL admission

The join operation SHALL, when the target meeting's admission policy is
`ALLOW_ALL`, admit the caller immediately: it SHALL generate a LiveKit access
token for the caller as a `PARTICIPANT` whose participant attributes carry the
caller's `role` and, when supplied, `avatarUrl`, and return `200 OK` with
`status` `APPROVED`, a generated correlation `requestId`, the LiveKit token, and
the room name. The issued token SHALL additionally carry a LiveKit room
configuration whose room name is `meeting-<meetingId>` and whose room metadata
carries the owning tenant identifier, so the LiveKit room exposes the tenant in
its metadata for later room/participant webhooks. The join operation SHALL NOT
record a participation log; recording the participation session is deferred to
the `participant_joined` webhook (the source of truth), which prevents orphan
logs for callers who obtain a token but never connect. Capacity SHALL be
enforced so that the number of active participants never exceeds the meeting's
`maxParticipants`, evaluated so that concurrent joins cannot exceed the limit.

#### Scenario: Open meeting admits the caller

- **WHEN** a caller joins an `ALLOW_ALL` meeting that has capacity
- **THEN** the response is `200` with `status` `APPROVED`, a generated
  `requestId`, a non-empty LiveKit token whose participant attributes carry
  `role` and (when supplied) `avatarUrl`, and the room name; no participation
  log is recorded by the join operation itself

#### Scenario: Participant token embeds tenant in room metadata

- **WHEN** a caller is admitted to an `ALLOW_ALL` meeting for a given tenant
- **THEN** the issued LiveKit token carries a room configuration for room
  `meeting-<meetingId>` whose metadata carries that tenant identifier

#### Scenario: Meeting at capacity is rejected

- **WHEN** a caller joins an `ALLOW_ALL` meeting whose active participants
  already equal `maxParticipants`
- **THEN** the response is `409` Problem Details with a meeting-full code and no
  new participation log is created

#### Scenario: Concurrent joins do not exceed capacity

- **WHEN** multiple callers join simultaneously such that only one seat remains
- **THEN** at most one additional caller is admitted and the others receive the
  meeting-full error, so the active participant count never exceeds
  `maxParticipants`

### Requirement: Immediate admission for the host and responding invitees under MANUAL_APPROVAL

The join operation SHALL, when the target meeting's admission policy is
`MANUAL_APPROVAL`, admit the caller immediately without host approval when the
caller is either the meeting's host or an active invitee of that meeting whose
invitation status is `ACCEPTED` or `TENTATIVE`. An invitation that has been
removed SHALL NOT confer eligibility. A caller who is not an invitee, whose
invitation status is `NEEDS_ACTION`, or whose invitation status is `DECLINED`
SHALL NOT be admitted immediately and SHALL instead follow the pending
join-request behavior.

Eligibility SHALL be determined from the invitation's persisted response status,
so an invitation accepted through the invitee response endpoints and one
accepted through an inbound email reply SHALL confer eligibility identically.

An immediately admitted caller SHALL receive the same outcome as immediate
admission under `ALLOW_ALL` admission: `200 OK` with `status` `APPROVED`, a
`requestId`, a LiveKit `PARTICIPANT` access token whose participant attributes
carry the caller's `role` and, when supplied, `avatarUrl`, and the room name,
with the token carrying the room configuration for room `meeting-<meetingId>`
whose metadata carries the owning tenant identifier. No participation log SHALL
be recorded by the join operation; recording remains deferred to the
`participant_joined` webhook.

Capacity SHALL be enforced for immediately admitted callers exactly as it is for
immediate admission under `ALLOW_ALL`: neither the host nor a responding invitee
SHALL be admitted when the number of active participants already equals the
meeting's `maxParticipants`.

#### Scenario: Host joins their own manual-approval meeting

- **WHEN** the account that hosts a `MANUAL_APPROVAL` meeting with available
  capacity joins it
- **THEN** the response is `200` with `status` `APPROVED`, a LiveKit token, and
  the room name, and no pending join request is created

#### Scenario: Accepted invitee is admitted immediately

- **WHEN** an active invitee whose invitation status is `ACCEPTED` joins a
  `MANUAL_APPROVAL` meeting with available capacity
- **THEN** the response is `200` with `status` `APPROVED`, a LiveKit token, and
  the room name, and no pending join request is created

#### Scenario: Tentative invitee is admitted immediately

- **WHEN** an active invitee whose invitation status is `TENTATIVE` joins a
  `MANUAL_APPROVAL` meeting with available capacity
- **THEN** the response is `200` with `status` `APPROVED`, a LiveKit token, and
  the room name, and no pending join request is created

#### Scenario: Invitation accepted by email reply confers the same eligibility

- **WHEN** an invitee whose invitation reached `ACCEPTED` through an inbound
  email reply joins a `MANUAL_APPROVAL` meeting with available capacity
- **THEN** the response is `200` with `status` `APPROVED` and a LiveKit token,
  identical to an invitation accepted through the invitee response endpoint

#### Scenario: Invitee who has not responded still requires approval

- **WHEN** an active invitee whose invitation status is `NEEDS_ACTION` joins a
  `MANUAL_APPROVAL` meeting
- **THEN** the response is `200` with `status` `PENDING`, no LiveKit token is
  returned, and a pending join request is created

#### Scenario: Declined invitee still requires approval

- **WHEN** an active invitee whose invitation status is `DECLINED` joins a
  `MANUAL_APPROVAL` meeting
- **THEN** the response is `200` with `status` `PENDING`, no LiveKit token is
  returned, and a pending join request is created

#### Scenario: Removed invitee still requires approval

- **WHEN** a caller whose invitation had status `ACCEPTED` but has since been
  removed from the meeting joins a `MANUAL_APPROVAL` meeting
- **THEN** the response is `200` with `status` `PENDING`, no LiveKit token is
  returned, and a pending join request is created

#### Scenario: Non-invitee still requires approval

- **WHEN** a caller who holds no invitation for the meeting joins a
  `MANUAL_APPROVAL` meeting
- **THEN** the response is `200` with `status` `PENDING`, no LiveKit token is
  returned, and a pending join request is created

#### Scenario: Immediate admission is refused when the meeting is full

- **WHEN** the host or an `ACCEPTED` invitee joins a `MANUAL_APPROVAL` meeting
  whose active participants already equal `maxParticipants`
- **THEN** the response is `409` `application/problem+json` with a meeting-full
  code, no LiveKit token is issued, and no pending join request is created

### Requirement: Reconciliation of a pending request superseded by immediate admission

The join operation SHALL resolve a superseded pending join request as approved
rather than leaving it pending, whenever a caller eligible for immediate
admission under `MANUAL_APPROVAL` already has a pending join request for the
same meeting and the same device. It SHALL transition that request to
`APPROVED`, persist the terminal outcome carrying the same LiveKit token and
room name returned in the response, remove the request from the meeting's
pending queue, and publish a join-approved event. The LiveKit token SHALL be
obtained before the request is transitioned, so a token-issuance failure leaves
the pending request unchanged.

When such a caller has no pending join request, the join operation SHALL publish
no join-request event, matching immediate admission under `ALLOW_ALL`.

When the superseded request is not in a state that permits approval — that is,
the host already denied it, which is terminal — the join operation SHALL still
admit the eligible caller, since eligibility derives from the caller's
invitation independently of any prior request. It SHALL leave that request's
status and stored outcome unchanged and SHALL publish no join-approved event.

#### Scenario: Stale pending request is approved on immediate admission

- **WHEN** an invitee who previously created a pending join request from a
  device responds `ACCEPTED` and then joins again from that same device
- **THEN** the response is `200` with `status` `APPROVED` and a LiveKit token,
  the previously pending request no longer appears in the meeting's pending
  queue, and a join-approved event carrying that request id, the issued token,
  and the room name is enqueued to the outbox

#### Scenario: Waiting requester receives the token for the reconciled request

- **WHEN** a pending join request is reconciled as approved by its requester's
  immediate admission
- **THEN** the request's terminal outcome is retrievable with `APPROVED` status
  and carries the same LiveKit token and room name returned in the join
  response, so a client subscribed to that request's stream resolves instead of
  waiting

#### Scenario: Immediate admission without a pending request publishes no event

- **WHEN** the host or an `ACCEPTED` invitee with no pending join request is
  immediately admitted to a `MANUAL_APPROVAL` meeting
- **THEN** no join-request created, approved, or denied event is enqueued to the
  outbox

#### Scenario: Token failure leaves the pending request untouched

- **WHEN** an eligible caller with an existing pending request is immediately
  admitted but LiveKit token issuance fails
- **THEN** the response is an error, the pending request remains `PENDING` in
  the meeting's pending queue, and no join-approved event is enqueued

#### Scenario: Already denied request is left untouched on immediate admission

- **WHEN** an eligible caller whose same-device request for the meeting was
  already denied by the host is immediately admitted
- **THEN** the response is `200` with `status` `APPROVED` and a LiveKit token,
  the denied request keeps its `DENIED` status and its stored outcome, and no
  join-approved event is enqueued

### Requirement: Pending join request under MANUAL_APPROVAL admission

The join operation SHALL, when the target meeting's admission policy is
`MANUAL_APPROVAL` and the caller is not eligible for immediate admission, create
a pending join request stored in Redis with a time-to-live, publish a
join-created event for host notification, and return `200 OK` with `status`
`PENDING` and the generated `requestId`, without issuing a LiveKit token. A
caller is eligible for immediate admission when they are the meeting's host or
an active invitee whose invitation status is `ACCEPTED` or `TENTATIVE`; such
callers are handled by the immediate-admission requirement instead. A repeated
join from the same device for the same meeting while a pending request exists
SHALL be idempotent and return the existing request rather than creating a
duplicate.

#### Scenario: Manual-approval meeting creates a pending request

- **WHEN** a caller who is neither the host nor a responding invitee joins a
  `MANUAL_APPROVAL` meeting
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

### Requirement: Per-source media publish restriction on participant tokens

The participant join token SHALL restrict publishing per track source using an
explicit allowed-sources grant derived from the meeting's settings (`microphone`
when `allowMicrophone`, `camera` when `allowVideo`, `screen_share` and
`screen_share_audio` when `allowScreenShare`), instead of a single
undifferentiated publish flag. When no media source is allowed by settings, the
token SHALL grant no publish permission rather than an unrestricted one. This
requirement applies to `PARTICIPANT` tokens; the `HOST` token SHALL retain full,
unrestricted publish permission regardless of settings.

#### Scenario: Screen share disabled excludes the source from the token

- **WHEN** a participant is issued a join token for a meeting whose settings
  have `allowScreenShare=false` and at least one other media source enabled
- **THEN** the issued token's allowed publish sources exclude screen share and
  screen-share audio

#### Scenario: All media sources disabled grants no publish permission

- **WHEN** a participant is issued a join token for a meeting whose settings
  have `allowMicrophone`, `allowVideo`, and `allowScreenShare` all disabled
- **THEN** the issued token grants no publish permission for any media source

#### Scenario: Host token is unrestricted regardless of settings

- **WHEN** a host is issued a join token for a meeting whose settings disable
  one or more media sources
- **THEN** the issued host token retains full publish permission unaffected by
  those settings
