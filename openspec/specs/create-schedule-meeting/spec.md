# create-schedule-meeting Specification

## Purpose

Enable creating future-dated `SCHEDULED` meetings via
`POST /api/1/meetings:schedule` with a header-resolved host identity. The
capability validates the scheduled time range with clock-skew tolerance,
registers invitees with hashed single-use invite tokens, publishes the
meeting-created and (when invitees exist) meeting-invitations-sent domain events
through the transactional outbox, and returns a token-free meeting snapshot
because no LiveKit room is provisioned at scheduling time.

## Requirements

### Requirement: Host identity resolved from request header

The system SHALL resolve the acting host's Jira `accountId` from a configurable
request header (default `X-Account-Id`) bound into a request-scoped account
context by the shared servlet filter, and SHALL NOT read the host identity from
the request body. The tenant SHALL be resolved from the `X-Tenant-ID` header.
The scheduled-meeting request body SHALL NOT contain a `host` object because no
LiveKit token is issued at scheduling time.

#### Scenario: Host resolved from header

- **WHEN** a client sends `POST /api/1/meetings:schedule` with
  `X-Account-Id: acc-123` and a valid `X-Tenant-ID`
- **THEN** the created meeting's host is `acc-123`, taken from the header and
  not from any body field

#### Scenario: Missing host header is rejected

- **WHEN** a client sends `POST /api/1/meetings:schedule` without an
  `X-Account-Id` header
- **THEN** the request fails with a Problem Details error and no meeting is
  created

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

### Requirement: Scheduled meeting time-range validation

The system SHALL require the scheduled `startTime` to be strictly before the
`endTime`, and SHALL require the `startTime` to not be in the past. To avoid
rejecting valid requests due to network or processing latency and minor client
clock skew, the system SHALL accept a `startTime` at or after
`now - toleranceWindow`, where the tolerance window is a small fixed grace
period. When the `startTime` is earlier than the allowed lower bound, the system
SHALL fail with a validation error identified by the machine-readable code
`MEETING_START_IN_PAST` and SHALL NOT create the meeting. The system SHALL NOT
enforce any maximum or minimum meeting duration, because scheduled start/end are
calendar metadata rather than runtime enforcement points.

#### Scenario: Future start time is accepted

- **WHEN** a request supplies a `startTime` comfortably in the future and an
  `endTime` after it
- **THEN** the meeting is created with type SCHEDULED and the time range is
  persisted

#### Scenario: Start time within clock-skew tolerance is accepted

- **WHEN** a request supplies a `startTime` slightly before the current instant
  but within the tolerance window
- **THEN** the meeting is created successfully and is not rejected as past

#### Scenario: Start time in the past is rejected

- **WHEN** a request supplies a `startTime` earlier than the tolerance lower
  bound
- **THEN** the response is a `400` Problem Details error with code
  `MEETING_START_IN_PAST` and no meeting is created

#### Scenario: Start not before end is rejected

- **WHEN** a request supplies a `startTime` equal to or after the `endTime`
- **THEN** the request fails validation and no meeting is created

### Requirement: Scheduled meeting lifecycle

Creating a scheduled meeting SHALL produce a `Meeting` of type `SCHEDULED` with
a unique join `shortCode`, SHALL leave it in `SCHEDULED` status (it is not
started and no LiveKit room is provisioned at creation), and SHALL persist
meeting state and any invitees within a single database transaction.

#### Scenario: Meeting is created scheduled and not started

- **WHEN** a scheduled meeting is created successfully
- **THEN** the persisted meeting has type `SCHEDULED` and status `SCHEDULED`,
  and no LiveKit token is issued and no `ParticipationLog` is created

#### Scenario: Short code uniqueness is enforced with retry

- **WHEN** a generated short code collides with an existing meeting's code
- **THEN** the system retries generation and, only after exhausting its bounded
  attempts, fails with a short-code-exhausted error without creating a meeting

#### Scenario: Persistence failure creates nothing

- **WHEN** persisting the meeting or invitees fails
- **THEN** the transaction rolls back and no meeting, invitee, or outbox row is
  left behind

### Requirement: Scheduled meeting invitee registration with invite tokens

When the request includes invitees, the system SHALL create one `MeetingInvitee`
per entry using the frontend-resolved `email`, `accountId`, and `displayName`
(all required), and SHALL generate a single-use invite token per invitee,
storing only the token's SHA-256 hash. The raw invite tokens SHALL NOT be
persisted and SHALL be carried only in the invitations event (embedded directly
in each invitee entry) for downstream delivery. Sending invitation emails is out
of scope for this capability.

#### Scenario: Invitees persisted with hashed tokens

- **WHEN** a scheduled meeting is created with two invitees
- **THEN** two `MeetingInvitee` rows are persisted in PENDING status, each with
  a stored token hash and no stored raw token

#### Scenario: No invitees produces no invitations event

- **WHEN** a scheduled meeting is created with an empty or absent invitees list
- **THEN** no `MeetingInvitee` row is created and no invitations event is
  enqueued, while the meeting is still created SCHEDULED

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
