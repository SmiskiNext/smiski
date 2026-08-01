## MODIFIED Requirements

### Requirement: Host-only manual meeting cancellation endpoint

The system SHALL expose `POST /api/1/meetings/{id}:cancel` to cancel a single
SCHEDULED meeting. The endpoint SHALL require the `edit-meeting` project
permission — if the caller's permission context does not contain `edit-meeting`,
the endpoint SHALL reject the request with `403 application/problem+json` and
code `NOT_AUTHORIZED` before executing the use case. The acting account SHALL be
resolved from the configured account header and the tenant SHALL be resolved
from the tenant context. Only the meeting host SHALL be authorized to cancel. A
successful cancellation SHALL transition the meeting status from `SCHEDULED` to
`CANCELED`, record the reason as `HOST_CANCELED`, load all active invitees,
publish a `MeetingCanceledEvent` with the invitee list through the transactional
outbox, and return `200 OK` with the full snapshot of the canceled meeting
including the reason.

No request body is required; the cancel reason is always `HOST_CANCELED` for
this endpoint.

#### Scenario: Host cancels a SCHEDULED meeting

- **WHEN** the host sends `POST /api/1/meetings/{id}:cancel` with the account
  header for a meeting whose status is `SCHEDULED` and has `edit-meeting`
  permission
- **THEN** the system transitions the meeting to `CANCELED` with reason
  `HOST_CANCELED`, publishes one `MeetingCanceledEvent` carrying the active
  invitee list, and returns `200 OK` with the full canceled meeting snapshot

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `POST /api/1/meetings/{id}:cancel`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and the meeting is not canceled

#### Scenario: Non-host cancellation is rejected

- **WHEN** an account that is not the meeting host sends a cancel request even
  with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and the meeting is not canceled
