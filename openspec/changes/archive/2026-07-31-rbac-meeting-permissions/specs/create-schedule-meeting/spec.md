## MODIFIED Requirements

### Requirement: Create scheduled meeting endpoint

The system SHALL expose `POST /api/1/meetings:schedule` accepting a body with a
required `title`, a required `description`, a required `issueLink` (`issueId`,
`issueKey`, `projectKey`), a required `settings` object (`admissionPolicy`,
`maxParticipants` in [2..100], `allowScreenShare`, `chatEnabled`,
`allowMicrophone`, `allowVideo`), a required `startTime` (ISO 8601), a required
`endTime` (ISO 8601), a required `zoneId` (the host's IANA time-zone id, e.g.
`Asia/Ho_Chi_Minh`), and an optional `invitees` array (each with a required
`email`, a required `accountId`, and a required `displayName`). The endpoint
SHALL require the `edit-meeting` project permission — if the caller's permission
context does not contain `edit-meeting`, the endpoint SHALL reject the request
with `403 application/problem+json` and code `NOT_AUTHORIZED` before executing
the use case. On success it SHALL return `201 Created` with a `Location` header
referencing the new meeting and a body containing the meeting snapshot
(including non-null `title`, `description`, `issueLink`, `startTime`, `endTime`,
and `zoneId`). The meeting snapshot SHALL NOT include the tenant identifier.

#### Scenario: Successful schedule creation returns snapshot without token

- **WHEN** a valid request is submitted with resolved host identity and tenant
  and a valid time range and the caller has `edit-meeting` permission
- **THEN** the response is `201 Created`, the body contains the meeting snapshot
  (id, host, shortCode, type SCHEDULED, status SCHEDULED, title, description,
  issueLink, settings, startTime, endTime, zoneId, createdAt), and the tenant
  identifier is absent from the body. The fields `title`, `description`,
  `issueLink`, `startTime`, `endTime`, and `zoneId` are never null.

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends `POST /api/1/meetings:schedule`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting is created
