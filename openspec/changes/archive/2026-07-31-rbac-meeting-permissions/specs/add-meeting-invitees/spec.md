## MODIFIED Requirements

### Requirement: Host-only invitee creation endpoint

The system SHALL expose `POST /api/1/meetings/{id}/invitees` for adding one or
more new invitees to a meeting. The endpoint SHALL require the `edit-meeting`
project permission — if the caller's permission context does not contain
`edit-meeting`, the endpoint SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` before executing the
use case. The acting account SHALL be resolved from the configured account
header, the tenant SHALL be resolved from the tenant context, and only the
meeting host SHALL be authorized to add invitees. The request body SHALL use the
shape `{ "invitees": [ { "email", "accountId", "displayName" } ] }`. A
successful call SHALL return `200 OK` with the full snapshot of the invitees
created by that call.

#### Scenario: Host adds new invitees

- **WHEN** the host sends a valid `POST /api/1/meetings/{id}/invitees` request
  with the account header and one or more invitees that are not yet active and
  has `edit-meeting` permission
- **THEN** the system creates each invitee with status `NEEDS_ACTION` and
  returns `200 OK` with the snapshot of the created invitees, each carrying
  `id`, `accountId`, `email`, `displayName`, `role`, `status`, `invitedAt`, and
  `respondedAt`

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `POST /api/1/meetings/{id}/invitees`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no invitees are added

#### Scenario: Non-host invitee addition is rejected

- **WHEN** an account that is not the meeting host sends an add-invitees request
  even with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and no invitees are created
