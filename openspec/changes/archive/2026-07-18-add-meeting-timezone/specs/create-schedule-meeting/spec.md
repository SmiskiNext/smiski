# create-schedule-meeting Specification (delta: add-meeting-timezone)

## MODIFIED Requirements

### Requirement: Create scheduled meeting endpoint

The system SHALL expose `POST /api/1/meetings:schedule` accepting a body with a
required `title`, a required `description`, a required `issueLink` (`issueId`,
`issueKey`, `projectKey`), a required `settings` object (`admissionPolicy`,
`maxParticipants` in [2..100], `allowScreenShare`, `chatEnabled`,
`allowMicrophone`, `allowVideo`), a required `timeRange` object (`startTime` and
`endTime` as ISO-8601 instants), a required top-level `zoneId` (the host's IANA
time-zone id, e.g. `Asia/Ho_Chi_Minh`), and an optional `invitees` array (each
with a required `email`, a required `accountId`, and a required `displayName`).
On success it SHALL return `201 Created` with a `Location` header referencing
the new meeting and a body containing the meeting snapshot (including non-null
`title`, `description`, `issueLink`, the scheduled `startTime`/`endTime`, and
the `zoneId`). The response SHALL NOT include a LiveKit token, and the meeting
snapshot SHALL NOT include the tenant identifier.

#### Scenario: Successful scheduled creation returns snapshot without token

- **WHEN** a valid request is submitted with resolved host identity and tenant,
  a future time range, and a valid `zoneId`
- **THEN** the response is `201 Created`, the body contains the meeting snapshot
  (id, host, shortCode, type SCHEDULED, status SCHEDULED, title, description,
  issueLink, settings, startTime, endTime, zoneId, createdAt), the body contains
  no `livekit` object, and the tenant identifier is absent from the body

#### Scenario: Missing required settings is a validation error

- **WHEN** a request omits the `settings` object or a required settings field
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and an `errors` entry for the missing field, and no meeting is created

#### Scenario: Missing time range is a validation error

- **WHEN** a request omits the `timeRange` object or either `startTime` or
  `endTime`
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created

#### Scenario: Missing zoneId is a validation error

- **WHEN** a request omits the `zoneId` field or sends a blank value
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created

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

#### Scenario: Invalid invitee email is a validation error

- **WHEN** a request includes an invitee whose `email` is blank or malformed
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting or invitee is persisted

#### Scenario: Missing invitee accountId or displayName is a validation error

- **WHEN** a request includes an invitee missing `accountId` or `displayName`
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting or invitee is persisted

### Requirement: Scheduled meeting event publication

Creating a scheduled meeting SHALL enqueue its domain events to the
transactional outbox within the creation transaction so they are durable if and
only if the creation commits. The system SHALL enqueue a meeting-created event
carrying a full aggregate snapshot that includes the scheduled `startTime`, the
scheduled `endTime`, and the host `zoneId`, and a meeting-invitations-sent event
carrying the host `zoneId`, the scheduled `startTime`, the scheduled `endTime`,
and the invite token embedded in each invitee entry only when invitees are
present. The system SHALL NOT enqueue a meeting-started event, because a
scheduled meeting is not started at creation. Each event SHALL be published to
Kafka as a CloudEvent by the shared outbox relay, and the domain SHALL remain
free of protocol-buffer and messaging types.

#### Scenario: Created event carries start time, end time, and zone

- **WHEN** a scheduled meeting is created and its transaction commits
- **THEN** the outbox contains a meeting-created row for that meeting whose
  snapshot carries the scheduled `startTime`, `endTime`, and `zoneId`, and
  contains no meeting-started row

#### Scenario: Invitations event carries zone, time range, and tokens

- **WHEN** a scheduled meeting is created with invitees
- **THEN** the outbox additionally contains a meeting-invitations-sent row whose
  payload carries the host `zoneId`, the scheduled `startTime` and `endTime`,
  and each invitee's raw invite token embedded in their entry

#### Scenario: Rolled-back creation enqueues no events

- **WHEN** the creation transaction is rolled back before commit
- **THEN** no meeting-created or meeting-invitations-sent outbox row exists for
  that meeting

## ADDED Requirements

### Requirement: Scheduled meeting host time zone

The system SHALL treat the scheduled meeting's `zoneId` as the host's time zone
captured at creation time and the authoritative display zone for the meeting.
The `zoneId` SHALL be a valid IANA time-zone id (region-based, e.g.
`Asia/Ho_Chi_Minh`); a fixed UTC offset alone SHALL NOT be accepted as the
stored representation. When the supplied `zoneId` is not a resolvable IANA zone
id, the system SHALL fail with a `400` Problem Details validation error and
SHALL NOT create the meeting. The resolved `zoneId` SHALL be persisted as a NOT
NULL attribute of the meeting alongside the UTC `startTime`/`endTime`, which
remain the authoritative instants and SHALL NOT be replaced by zoned
representations.

#### Scenario: Valid IANA zone is persisted and echoed

- **WHEN** a scheduled meeting is created with `zoneId` set to a valid IANA zone
  id such as `Asia/Ho_Chi_Minh`
- **THEN** the meeting is persisted with that `zoneId` as a non-null attribute
  and the same value appears in the creation response snapshot

#### Scenario: Unknown zone id is rejected

- **WHEN** a request supplies a `zoneId` that is not a resolvable IANA zone id
  (e.g. `Mars/Phobos` or a bare offset such as `+07:00`)
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and no meeting is created
