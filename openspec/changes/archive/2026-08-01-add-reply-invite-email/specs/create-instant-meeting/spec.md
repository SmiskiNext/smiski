## MODIFIED Requirements

### Requirement: Instant meeting lifecycle

Creating an instant meeting SHALL produce a `Meeting` of type `INSTANT` with a
unique join `shortCode`, SHALL immediately transition it to `LIVE`, and SHALL
persist meeting state and any invitees within a single database transaction.
Creation SHALL assign the meeting a scheduled time range whose `startTime` is
the creation instant and whose `endTime` is the creation instant plus a
configurable default instant-meeting duration, so the meeting always carries a
valid, `startTime`-before-`endTime` range. Host participation logging is
deferred to the `participant_joined` LiveKit webhook (out of scope for this
capability).

#### Scenario: Meeting is created live

- **WHEN** an instant meeting is created successfully
- **THEN** the persisted meeting has type `INSTANT` and status `LIVE`, and no
  `ParticipationLog` is created at creation time (host participation is recorded
  later via the LiveKit webhook)

#### Scenario: Instant meeting carries a start and end time

- **WHEN** an instant meeting is created successfully
- **THEN** the persisted meeting has a non-null `startTime` equal to the
  creation instant and a non-null `endTime` equal to the creation instant plus
  the configured default instant-meeting duration, with `startTime` strictly
  before `endTime`

#### Scenario: Short code uniqueness is enforced with retry

- **WHEN** a generated short code collides with an existing meeting's code
- **THEN** the system retries generation and, only after exhausting its bounded
  attempts, fails with a short-code-exhausted error without creating a meeting

#### Scenario: Persistence failure creates nothing

- **WHEN** persisting the meeting or invitees fails
- **THEN** the transaction rolls back and no meeting, invitee, or outbox row is
  left behind

### Requirement: Instant meeting event publication

Creating an instant meeting SHALL enqueue its domain events to the transactional
outbox within the creation transaction so they are durable if and only if the
creation commits. The system SHALL enqueue a meeting-created event (carrying a
full aggregate snapshot including the host `zoneId`) and a meeting-started event
(also carrying a full aggregate snapshot including the host `zoneId` plus the
LiveKit room name) for every instant meeting, and a meeting-invitations-sent
event carrying the host `zoneId` only when invitees are present. Because an
instant meeting is assigned a scheduled time range at creation, its
meeting-invitations-sent event SHALL carry the meeting's `startTime` (the
creation instant) and `endTime` (the creation instant plus the configured
default duration) rather than absent times. Each event SHALL be published to
Kafka as a CloudEvent by the shared outbox relay, and the domain SHALL remain
free of protocol-buffer and messaging types.

#### Scenario: Base events enqueued atomically with zone

- **WHEN** an instant meeting is created and its transaction commits
- **THEN** the outbox contains a meeting-created row and a meeting-started row
  for that meeting, each unpublished, with the meeting id as aggregate id and
  each snapshot carrying the host `zoneId`

#### Scenario: Invitations event carries zone and times

- **WHEN** an instant meeting is created with invitees
- **THEN** the outbox additionally contains a meeting-invitations-sent row whose
  payload carries the host `zoneId` and a non-null `startTime` and `endTime`
  reflecting the assigned instant time range

#### Scenario: Rolled-back creation enqueues no events

- **WHEN** the creation transaction is rolled back before commit
- **THEN** no meeting-created, meeting-started, or meeting-invitations-sent
  outbox row exists for that meeting

#### Scenario: Events relayed to Kafka as CloudEvents

- **WHEN** the shared outbox relay processes the enqueued rows
- **THEN** each event is published to its topic as a CloudEvent keyed by the
  tenant identifier and the row is marked published

### Requirement: Instant meeting host time zone

The system SHALL treat the instant meeting's `zoneId` as the host's time zone
captured at creation time and the authoritative display zone for the meeting.
The `zoneId` SHALL be a valid IANA time-zone id (region-based, e.g.
`Asia/Ho_Chi_Minh`); a fixed UTC offset alone SHALL NOT be accepted as the
stored representation. When the supplied `zoneId` is not a resolvable IANA zone
id, the system SHALL fail with a `400` Problem Details validation error and
SHALL NOT create the meeting. The resolved `zoneId` SHALL be persisted as a NOT
NULL attribute of the meeting alongside the meeting's assigned `startTime` and
`endTime`.

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

## ADDED Requirements

### Requirement: Configurable default instant-meeting duration

The system SHALL read the default instant-meeting duration from externalized
configuration and SHALL use it to derive an instant meeting's `endTime` from its
creation instant. When the configuration is absent, the system SHALL apply a
built-in default duration so that instant meetings always receive a valid time
range. The configured or default duration SHALL be strictly positive so that the
resulting `startTime` is always before the `endTime`.

#### Scenario: Configured duration is applied

- **WHEN** the default instant-meeting duration is configured to a specific
  positive value and an instant meeting is created
- **THEN** the meeting's `endTime` equals its `startTime` plus the configured
  duration

#### Scenario: Missing configuration falls back to the built-in default

- **WHEN** no default instant-meeting duration is configured and an instant
  meeting is created
- **THEN** the meeting is still created with a valid time range using the
  built-in default duration, with `startTime` strictly before `endTime`
