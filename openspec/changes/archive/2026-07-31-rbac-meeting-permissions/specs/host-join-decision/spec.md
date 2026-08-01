## MODIFIED Requirements

### Requirement: Host accepts pending join requests

The meet service SHALL expose `POST /meetings/{id}/join-requests:accept`
allowing the meeting host to accept one or more pending join requests. The
endpoint SHALL require the `edit-meeting` project permission — if the caller's
permission context does not contain `edit-meeting`, the endpoint SHALL reject
the request with `403 application/problem+json` and code `NOT_AUTHORIZED` before
executing the use case. The request body SHALL carry a non-empty `requestIds`
array of UUIDs. The account identifier SHALL be taken from the request account
context and the tenant from the tenant context; neither SHALL be accepted in the
body. Only the account that hosts the meeting SHALL be permitted to accept; a
non-host caller SHALL receive `403` `application/problem+json` with code
`NOT_OWNER`. A missing account header or an empty/malformed body SHALL yield
`400` `application/problem+json` with code `VALIDATION_ERROR`. A meeting that
does not exist for the current tenant SHALL yield `404` with a meeting-not-found
code. On an authorized, well-formed request the response SHALL be `200 OK`
carrying a `results` array with one entry per submitted `requestId`.

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
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no join requests are processed

#### Scenario: Non-host acceptance is rejected

- **WHEN** an account that is not the meeting host sends an accept request even
  with `edit-meeting` permission
- **THEN** the system returns `403` `application/problem+json` with code
  `NOT_OWNER` and no join requests are processed

### Requirement: Host declines pending join requests

The meet service SHALL expose `POST /meetings/{id}/join-requests:decline`
allowing the meeting host to decline one or more pending join requests. The
endpoint SHALL require the `edit-meeting` project permission — if the caller's
permission context does not contain `edit-meeting`, the endpoint SHALL reject
the request with `403 application/problem+json` and code `NOT_AUTHORIZED` before
executing the use case. The request body SHALL carry a non-empty `requestIds`
array of UUIDs. The account identifier SHALL be taken from the request account
context and the tenant from the tenant context; neither SHALL be accepted in the
body. Only the account that hosts the meeting SHALL be permitted to decline; a
non-host caller SHALL receive `403` `application/problem+json` with code
`NOT_OWNER`. A missing account header or an empty/malformed body SHALL yield
`400` `application/problem+json` with code `VALIDATION_ERROR`. A meeting that
does not exist for the current tenant SHALL yield `404` with a meeting-not-found
code. On an authorized, well-formed request the response SHALL be `200 OK`
carrying a `results` array with one entry per submitted `requestId`.

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
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no join requests are processed

#### Scenario: Non-host decline is rejected

- **WHEN** an account that is not the meeting host sends a decline request even
  with `edit-meeting` permission
- **THEN** the system returns `403` `application/problem+json` with code
  `NOT_OWNER` and no join requests are processed
