# join-request-approval Delta Specification

## MODIFIED Requirements

### Requirement: Host accepts pending join requests

The meet service SHALL expose `POST /meetings/{id}/join-requests:accept`
allowing the meeting host to accept one or more pending join requests. The
endpoint SHALL require the `edit-meeting` project permission — if the caller's
permission context does not contain `edit-meeting`, the endpoint SHALL reject
the request with a `403` Problem Details response and code `NOT_AUTHORIZED`
before executing the use case. The request body SHALL carry a non-empty
`requestIds` array of UUIDs. The account identifier SHALL be taken from the
request account context and the tenant from the tenant context; neither SHALL be
accepted in the body. Only the account that hosts the meeting SHALL be permitted
to accept; a non-host caller SHALL receive `403` Problem Details with code
`NOT_OWNER`. A missing account header or an empty/malformed body SHALL yield
`400` Problem Details with code `VALIDATION_ERROR`. A meeting that does not
exist for the current tenant SHALL yield `404` with a meeting-not-found code. On
an authorized, well-formed request the response SHALL be `200 OK` carrying a
`results` array with one entry per submitted `requestId`.

For each accepted request the meet service SHALL enforce the meeting's
`maxParticipants` capacity via optimistic pre-generation of LiveKit tokens
followed by pessimistic final verification. Token generation SHALL occur outside
any database lock. A pessimistic lock on the meeting row SHALL be acquired only
for final capacity verification, request state transition, outcome persistence,
queue removal, and event publishing. Each accepted request generates a LiveKit
`PARTICIPANT` access token whose room is `meeting-<meetingId>` and whose room
metadata carries the owning tenant. After capacity is verified under lock, the
request transitions to `APPROVED`, the terminal outcome (including token and
room name) is persisted, the request is removed from the meeting's pending
queue, and a `JoinRequestApprovedEvent` is published.

#### Scenario: Host accepts pending requests

- **WHEN** the host sends a valid `POST /meetings/{id}/join-requests:accept`
  request with the account header and one or more valid pending request ids and
  has `edit-meeting` permission
- **THEN** each request is approved, a LiveKit token is issued for each, and the
  response is `200 OK` with a `results` array containing one entry per submitted
  request id

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `POST /meetings/{id}/join-requests:accept`
- **THEN** the response is `403` Problem Details with code `NOT_AUTHORIZED` and
  no join requests are processed

#### Scenario: Non-host acceptance is rejected

- **WHEN** an account that is not the meeting host sends an accept request even
  with `edit-meeting` permission
- **THEN** the system returns `403` Problem Details with code `NOT_OWNER` and no
  join requests are processed

#### Scenario: Tokens are generated before lock acquisition

- **WHEN** the host accepts multiple pending requests
- **THEN** all LiveKit tokens for the batch are generated concurrently before
  any database lock is acquired

### Requirement: Accept processing is best-effort per item

The accept operation SHALL process each submitted request id independently and
return a per-item result rather than failing the whole batch when some items
cannot be accepted. Each result entry SHALL carry the `requestId` and a `status`
of `APPROVED`, `DENIED`, or `FAILED`. `APPROVED` entries SHALL carry the LiveKit
`token` and `roomName`. `FAILED` entries SHALL carry a machine-readable `reason`
and SHALL NOT carry a token. An id that is unknown, already terminal, expired,
or belongs to a different meeting SHALL become a `FAILED` item with the
corresponding reason. Capacity SHALL be enforced via pessimistic verification
under lock: when the meeting reaches `maxParticipants` during the batch, further
approvals SHALL become `FAILED` items with a meeting-full reason so that the
number of admitted participants never exceeds `maxParticipants`. The capacity
counter SHALL be decremented in memory as each request is approved under the
single lock hold.

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

#### Scenario: Capacity verification occurs under lock after token generation

- **WHEN** the host accepts pending requests
- **THEN** all tokens are generated before the database lock is acquired, and
  capacity is verified authoritatively under the lock before any request is
  marked approved
