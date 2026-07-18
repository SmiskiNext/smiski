# create-instant-meeting Specification (delta: add-meeting-timezone)

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
`displayName`). On success it SHALL return `201 Created` with a `Location`
header referencing the new meeting and a body containing the meeting snapshot
(including non-null `title`, `description`, `issueLink`, and `zoneId`) and the
host's LiveKit access token. The meeting snapshot SHALL NOT include the tenant
identifier.

#### Scenario: Successful instant creation returns snapshot and host token

- **WHEN** a valid request is submitted with resolved host identity and tenant
  and a valid `zoneId`
- **THEN** the response is `201 Created`, the body contains the meeting snapshot
  (id, host, shortCode, type INSTANT, status LIVE, title, description,
  issueLink, settings, zoneId, createdAt) and a `livekit` object with a
  non-empty `token` and the `roomName` `meeting-<meetingId>`, and the tenant
  identifier is absent from the body. The fields `title`, `description`,
  `issueLink`, and `zoneId` are never null.

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

### Requirement: Instant meeting event publication

Creating an instant meeting SHALL enqueue its domain events to the transactional
outbox within the creation transaction so they are durable if and only if the
creation commits. The system SHALL enqueue a meeting-created event (carrying a
full aggregate snapshot including the host `zoneId`) and a meeting-started event
(also carrying a full aggregate snapshot including the host `zoneId` plus the
LiveKit room name) for every instant meeting, and a meeting-invitations-sent
event carrying the host `zoneId` and the invite token embedded in each invitee
entry only when invitees are present. Because an instant meeting has no
scheduled time range, its meeting-invitations-sent `startTime` and `endTime`
SHALL be absent. Each event SHALL be published to Kafka as a CloudEvent by the
shared outbox relay, and the domain SHALL remain free of protocol-buffer and
messaging types.

#### Scenario: Base events enqueued atomically with zone

- **WHEN** an instant meeting is created and its transaction commits
- **THEN** the outbox contains a meeting-created row and a meeting-started row
  for that meeting, each unpublished, with the meeting id as aggregate id and
  each snapshot carrying the host `zoneId`

#### Scenario: Invitations event carries zone and tokens

- **WHEN** an instant meeting is created with invitees
- **THEN** the outbox additionally contains a meeting-invitations-sent row whose
  payload carries the host `zoneId`, absent `startTime`/`endTime`, and each
  invitee's raw invite token embedded in their entry

#### Scenario: Rolled-back creation enqueues no events

- **WHEN** the creation transaction is rolled back before commit
- **THEN** no meeting-created, meeting-started, or meeting-invitations-sent
  outbox row exists for that meeting

#### Scenario: Events relayed to Kafka as CloudEvents

- **WHEN** the shared outbox relay processes the enqueued rows
- **THEN** each event is published to its topic as a CloudEvent keyed by the
  tenant identifier and the row is marked published

## ADDED Requirements

### Requirement: Instant meeting host time zone

The system SHALL treat the instant meeting's `zoneId` as the host's time zone
captured at creation time and the authoritative display zone for the meeting.
The `zoneId` SHALL be a valid IANA time-zone id (region-based, e.g.
`Asia/Ho_Chi_Minh`); a fixed UTC offset alone SHALL NOT be accepted as the
stored representation. When the supplied `zoneId` is not a resolvable IANA zone
id, the system SHALL fail with a `400` Problem Details validation error and
SHALL NOT create the meeting. The resolved `zoneId` SHALL be persisted as a NOT
NULL attribute of the meeting even though an instant meeting carries no
scheduled `startTime`/`endTime`.

#### Scenario: Valid IANA zone is persisted and echoed

- **WHEN** an instant meeting is created with `zoneId` set to a valid IANA zone
  id such as `Asia/Ho_Chi_Minh`
- **THEN** the meeting is persisted with that `zoneId` as a non-null attribute
  and the same value appears in the creation response snapshot

#### Scenario: Unknown zone id is rejected

- **WHEN** a request supplies a `zoneId` that is not a resolvable IANA zone id
  (e.g. `Mars/Phobos` or a bare offset such as `+07:00`)
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created
