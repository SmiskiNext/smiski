# meeting-join Delta Specification

## MODIFIED Requirements

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
enforced on a best-effort basis so that the number of active participants does
not exceed the meeting's `maxParticipants`, evaluated via optimistic pre-check
followed by pessimistic final verification. The optimistic pre-check SHALL read
meeting and capacity without locking, failing fast when capacity is exhausted.
Token generation SHALL occur outside any database lock. Final verification SHALL
re-check capacity under a pessimistic lock immediately before returning the
token, failing with `MeetingFull` if capacity was exhausted during token
generation.

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

#### Scenario: Optimistic pre-check detects full capacity early

- **WHEN** a caller joins an `ALLOW_ALL` meeting whose optimistic capacity count
  already equals `maxParticipants`
- **THEN** the response is `409` Problem Details with a meeting-full code
  without generating a token or acquiring a database lock

#### Scenario: Race between optimistic and final check is handled

- **WHEN** optimistic capacity check passes but capacity is exhausted before
  final verification completes
- **THEN** the response is `409` Problem Details with a meeting-full code, the
  generated token is discarded, and a warning log entry is recorded for
  observability

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

Capacity SHALL be enforced for immediately admitted callers via the same
optimistic pre-check and pessimistic final verification strategy as immediate
admission under `ALLOW_ALL`: neither the host nor a responding invitee SHALL be
admitted when the number of active participants already equals the meeting's
`maxParticipants`. Eligibility determination (invitee lookup) SHALL occur
without database locking. Token generation SHALL occur outside any database
lock. The pessimistic lock SHALL be acquired only for final capacity
verification and Redis request reconciliation.

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
the pending request unchanged. Reconciliation SHALL occur under a pessimistic
lock acquired only for the final verification phase, after token generation
completes.

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
duplicate. This operation SHALL NOT acquire a database lock, as no capacity
check or token generation is performed for pending requests.

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

#### Scenario: Pending request creation does not acquire database lock

- **WHEN** a non-eligible caller creates a pending join request
- **THEN** no database lock is acquired on the meeting row during the operation
