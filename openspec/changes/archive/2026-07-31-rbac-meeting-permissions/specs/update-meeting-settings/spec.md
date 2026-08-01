## MODIFIED Requirements

### Requirement: Host-only meeting settings replacement endpoint

The system SHALL expose `PUT /api/1/meetings/{id}/settings` for replacing the
entire meeting settings block. The endpoint SHALL require the `edit-meeting`
project permission — if the caller's permission context does not contain
`edit-meeting`, the endpoint SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` before executing the
use case. The acting account SHALL be resolved from the configured account
header, the tenant SHALL be resolved from the tenant context, and only the
meeting host SHALL be authorized to change settings. The request SHALL carry the
full settings representation (`admissionPolicy`, `maxParticipants`,
`allowMicrophone`, `allowVideo`, `allowScreenShare`, `chatEnabled`). A
successful replacement SHALL return `200 OK` with the updated settings snapshot.

#### Scenario: Host replaces settings

- **WHEN** the host sends a valid `PUT /api/1/meetings/{id}/settings` request
  with the account header and has `edit-meeting` permission
- **THEN** the system persists the new settings and returns `200 OK` with the
  updated settings snapshot

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `PUT /api/1/meetings/{id}/settings`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and the settings are not changed

#### Scenario: Non-host settings change is rejected

- **WHEN** an account that is not the meeting host sends a settings request even
  with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and does not change the settings
