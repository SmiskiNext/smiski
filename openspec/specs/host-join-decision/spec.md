# host-join-decision Specification

## Purpose

TBD - created by archiving change add-host-join-decision. Update Purpose after
archive.

## Requirements

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

For each accepted request the meet service SHALL, while holding a pessimistic
lock on the meeting row, enforce the meeting's `maxParticipants` capacity,
generate a LiveKit `PARTICIPANT` access token whose room is
`meeting-<meetingId>` and whose room metadata carries the owning tenant,
transition the request to `APPROVED`, persist the terminal outcome including the
token and room name, remove the request from the meeting's pending queue, and
publish a `JoinRequestApprovedEvent`.

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
allowing the meeting host to decline one or more pending join requests. The
endpoint SHALL require the `edit-meeting` project permission — if the caller's
permission context does not contain `edit-meeting`, the endpoint SHALL reject
the request with a `403` Problem Details response and code `NOT_AUTHORIZED`
before executing the use case. The request body SHALL carry a non-empty
`requestIds` array of UUIDs. The account identifier SHALL be taken from the
request account context and the tenant from the tenant context; neither SHALL be
accepted in the body. Only the account that hosts the meeting SHALL be permitted
to decline; a non-host caller SHALL receive `403` Problem Details with code
`NOT_OWNER`. A missing account header or an empty/malformed body SHALL yield
`400` Problem Details with code `VALIDATION_ERROR`. A meeting that does not
exist for the current tenant SHALL yield `404` with a meeting-not-found code. On
an authorized, well-formed request the response SHALL be `200 OK` carrying a
`results` array with one entry per submitted `requestId`.

For each declined request the meet service SHALL transition the request to
`DENIED`, persist the terminal outcome, remove the request from the meeting's
pending queue, and publish a `JoinRequestDeniedEvent`.

#### Scenario: Host declines pending requests

- **WHEN** the host sends a valid `POST /meetings/{id}/join-requests:decline`
  request with the account header and one or more valid pending request ids and
  has `edit-meeting` permission
- **THEN** each request is denied and the response is `200 OK` with a `results`
  array containing one entry per submitted request id

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `POST /meetings/{id}/join-requests:decline`
- **THEN** the response is `403` Problem Details with code `NOT_AUTHORIZED` and
  no join requests are processed

#### Scenario: Non-host decline is rejected

- **WHEN** an account that is not the meeting host sends a decline request even
  with `edit-meeting` permission
- **THEN** the system returns `403` Problem Details with code `NOT_OWNER` and no
  join requests are processed

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
