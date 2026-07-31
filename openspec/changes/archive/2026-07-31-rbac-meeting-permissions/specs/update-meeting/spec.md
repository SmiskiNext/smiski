## MODIFIED Requirements

### Requirement: Host-only meeting update endpoint

The system SHALL expose `PUT /api/1/meetings/{id}` for updating a meeting. The
endpoint SHALL require the `edit-meeting` project permission — if the caller's
permission context does not contain `edit-meeting`, the endpoint SHALL reject
the request with `403 application/problem+json` and code `NOT_AUTHORIZED` before
executing the use case. The acting account SHALL be resolved from the configured
account header, the tenant SHALL be resolved from the tenant context, and only
the meeting host SHALL be authorized to update the meeting. A successful update
SHALL return `200 OK` with the complete meeting snapshot excluding the tenant
identifier.

#### Scenario: Host updates a meeting

- **WHEN** the host sends a valid `PUT /api/1/meetings/{id}` request with the
  account header and has `edit-meeting` permission
- **THEN** the system persists the permitted changes and returns `200 OK` with
  the full meeting snapshot

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends `PUT /api/1/meetings/{id}`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and the meeting is not updated

#### Scenario: Non-host update is rejected

- **WHEN** an account that is not the meeting host sends an update request even
  with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and does not change the meeting
