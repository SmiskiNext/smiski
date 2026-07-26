## ADDED Requirements

### Requirement: Host accepts pending join requests

The meet service SHALL expose `POST /meetings/{id}/join-requests:accept`
allowing the meeting host to accept one or more pending join requests. The
request body SHALL carry a non-empty `requestIds` array of UUIDs. The account
identifier SHALL be taken from the request account context and the tenant from
the tenant context; neither SHALL be accepted in the body. Only the account that
hosts the meeting SHALL be permitted to accept; a non-host caller SHALL receive
`403` `application/problem+json` with code `NOT_OWNER`. A missing account header
or an empty/malformed body SHALL yield `400` `application/problem+json` with
code `VALIDATION_ERROR`. A meeting that does not exist for the current tenant
SHALL yield `404` with a meeting-not-found code. On an authorized, well-formed
request the response SHALL be `200 OK` carrying a `results` array with one entry
per submitted `requestId`.

For each accepted request the meet service SHALL, while holding a pessimistic
lock on the meeting row, enforce the meeting's `maxParticipants` capacity,
generate a LiveKit `PARTICIPANT` access token whose room is
`meeting-<meetingId>` and whose room metadata carries the owning tenant,
transition the request to `APPROVED`, persist the terminal outcome including the
token and room name, remove the request from the meeting's pending queue, and
publish an approved event. The accept operation SHALL NOT record a participation
log; recording is deferred to the `participant_joined` webhook.

#### Scenario: Host accepts a single pending request

- **WHEN** the host calls accept with one pending request id for a meeting that
  has capacity
- **THEN** the response is `200` and the matching result entry has `status`
  `APPROVED` with a non-empty LiveKit token and the room name, the request is
  removed from the pending queue, and its terminal outcome is persisted

#### Scenario: Non-host caller is rejected

- **WHEN** an account that is not the meeting host calls accept
- **THEN** the response is `403` `application/problem+json` with code
  `NOT_OWNER` and no request is modified

#### Scenario: Empty request id list is a validation error

- **WHEN** the host calls accept with an empty `requestIds` array or a missing
  body
- **THEN** the response is `400` `application/problem+json` with code
  `VALIDATION_ERROR`

#### Scenario: Unknown meeting

- **WHEN** the host calls accept for a meeting id that does not exist for the
  current tenant
- **THEN** the response is `404` `application/problem+json` with a
  meeting-not-found code

### Requirement: Accept processing is best-effort per item

The accept operation SHALL process each submitted request id independently and
return a per-item result rather than failing the whole batch when some items
cannot be accepted. Each result entry SHALL carry the `requestId` and a `status`
of `APPROVED`, `DENIED`, or `FAILED`. `APPROVED` entries SHALL carry the LiveKit
`token` and `roomName`. `FAILED` entries SHALL carry a machine-readable `reason`
and SHALL NOT carry a token. An id that is unknown, already terminal, expired,
or belongs to a different meeting SHALL become a `FAILED` item with the
corresponding reason. When the meeting reaches `maxParticipants` during the
batch, further approvals SHALL become `FAILED` items with a meeting-full reason
so that the number of admitted participants never exceeds `maxParticipants`.

#### Scenario: Partial batch admits available seats only

- **WHEN** the host accepts more pending requests than the meeting's remaining
  capacity
- **THEN** the response is `200`, exactly the number of requests that fit are
  `APPROVED` with tokens, and the remainder are `FAILED` with a meeting-full
  reason, and the active participant count never exceeds `maxParticipants`

#### Scenario: Unknown or terminal id fails only that item

- **WHEN** the host accepts a batch containing a pending id and an unknown or
  already-decided id
- **THEN** the pending id is `APPROVED` and the unknown or already-decided id is
  `FAILED` with a reason, and the response status is still `200`

#### Scenario: Expired request fails that item

- **WHEN** the host accepts a request whose pending TTL has elapsed
- **THEN** that result entry is `FAILED` with a join-request-expired reason

### Requirement: Host declines pending join requests

The meet service SHALL expose `POST /meetings/{id}/join-requests:decline`
allowing the meeting host to decline one or more pending join requests, with the
same body shape, context-derived identity, host-only authorization, and
request-level error semantics as accept. For each declined request the meet
service SHALL transition it to `DENIED`, persist the terminal outcome, remove it
from the pending queue, and publish a denied event. Decline SHALL be best-effort
per item, returning a `results` array whose entries are `DENIED` or `FAILED`
with a reason.

#### Scenario: Host declines a pending request

- **WHEN** the host calls decline with one pending request id
- **THEN** the response is `200`, the result entry has `status` `DENIED` with no
  token, the request is removed from the pending queue, and its terminal outcome
  is persisted

#### Scenario: Decline is best-effort per item

- **WHEN** the host declines a batch containing a pending id and an unknown id
- **THEN** the pending id is `DENIED` and the unknown id is `FAILED` with a
  reason, and the response status is `200`

#### Scenario: Non-host caller is rejected

- **WHEN** an account that is not the meeting host calls decline
- **THEN** the response is `403` `application/problem+json` with code
  `NOT_OWNER` and no request is modified

### Requirement: Terminal join request outcome persistence

The meet service SHALL persist the terminal outcome of every accepted or
declined join request in a store keyed by request id with a time-to-live aligned
to the requester notification window, so a requester that learns its outcome
after the fact can still retrieve it. An approved outcome SHALL retain the
LiveKit token and room name; a denied outcome SHALL retain no token.

#### Scenario: Approved outcome retains the token

- **WHEN** a join request is accepted
- **THEN** its persisted outcome has status `APPROVED` and retains the LiveKit
  token and room name until the outcome's TTL elapses

#### Scenario: Denied outcome retains no token

- **WHEN** a join request is declined
- **THEN** its persisted outcome has status `DENIED` and carries no LiveKit
  token
