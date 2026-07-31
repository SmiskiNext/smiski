## MODIFIED Requirements

### Requirement: Create instant meeting endpoint

The system SHALL expose `POST /api/1/meetings:instant` accepting a body with a
required `title`, a required `description`, a required `issueLink` (`issueId`,
`issueKey`, `projectKey`), a required `settings` object (`admissionPolicy`,
`maxParticipants` in [2..100], `allowScreenShare`, `chatEnabled`,
`allowMicrophone`, `allowVideo`), a required `host` object (`displayName`,
`deviceId`, and an optional `avatarUrl`), a required top-level `zoneId` (the
host's IANA time-zone id, e.g. `Asia/Ho_Chi_Minh`), and an optional `invitees`
array (each with a required `email`, a required `accountId`, and a required
`displayName`). The endpoint SHALL require the `edit-meeting` project permission
— if the caller's permission context does not contain `edit-meeting`, the
endpoint SHALL reject the request with `403 application/problem+json` and code
`NOT_AUTHORIZED` before executing the use case. On success it SHALL return
`201 Created` with a `Location` header referencing the new meeting and a body
containing the meeting snapshot (including non-null `title`, `description`,
`issueLink`, and `zoneId`) and the host's LiveKit access token. The meeting
snapshot SHALL NOT include the tenant identifier.

#### Scenario: Successful instant creation returns snapshot and host token

- **WHEN** a valid request is submitted with resolved host identity and tenant
  and a valid `zoneId` and the caller has `edit-meeting` permission
- **THEN** the response is `201 Created`, the body contains the meeting snapshot
  (id, host, shortCode, type INSTANT, status LIVE, title, description,
  issueLink, settings, zoneId, createdAt) and a `livekit` object with a
  non-empty `token` and the `roomName` `meeting-<meetingId>`, and the tenant
  identifier is absent from the body. The fields `title`, `description`,
  `issueLink`, and `zoneId` are never null.

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends `POST /api/1/meetings:instant`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting is created

#### Scenario: Missing required settings is a validation error

- **WHEN** a request omits the `settings` object or a required settings field
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and an `errors` entry for the missing field, and no meeting is created

#### Scenario: Missing host identity fields is a validation error

- **WHEN** a request omits `host.displayName` or `host.deviceId`
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created

#### Scenario: Missing zoneId is a validation error

- **WHEN** a request omits the `zoneId` field or sends a blank value
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created

#### Scenario: Invalid invitee email is a validation error

- **WHEN** a request includes an invitee whose `email` is blank or malformed
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting or invitee is persisted

#### Scenario: Missing invitee accountId or displayName is a validation error

- **WHEN** a request includes an invitee missing `accountId` or `displayName`
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting or invitee is persisted

#### Scenario: Missing description is a validation error

- **WHEN** a request omits the `description` field or sends a blank value
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created

#### Scenario: Missing issueLink is a validation error

- **WHEN** a request omits the `issueLink` object
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created

#### Scenario: maxParticipants exceeds 100 is a validation error

- **WHEN** a request sends `settings.maxParticipants` greater than 100
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created
