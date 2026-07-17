# create-instant-meeting Specification

## Purpose

TBD - created by archiving change create-instant-meeting. Update Purpose after
archive.

## Requirements

### Requirement: Host identity resolved from request header

The system SHALL resolve the acting host's Jira `accountId` from a configurable
request header (default `X-Account-Id`) bound into a request-scoped account
context by a shared servlet filter, and SHALL NOT read the host identity from
the request body. The tenant SHALL continue to be resolved from the
`X-Tenant-ID` header. The account context SHALL be cleared at the end of each
request. Internally, the `accountId` (from the header) is carried inside the
`Host` record of the command — it is never a top-level command field.

#### Scenario: Host resolved from header

- **WHEN** a client sends `POST /api/1/meetings:instant` with
  `X-Account-Id: acc-123` and a valid `X-Tenant-ID`
- **THEN** the created meeting's host is `acc-123` and the value is taken from
  the header, not from any body field

#### Scenario: Missing host header is rejected

- **WHEN** a client sends `POST /api/1/meetings:instant` without an
  `X-Account-Id` header
- **THEN** the request fails with a Problem Details error and no meeting is
  created

#### Scenario: Account context does not leak across requests

- **WHEN** a request carrying `X-Account-Id` completes and a later request
  arrives without the header
- **THEN** the later request does not observe the previous request's account
  identity

### Requirement: Create instant meeting endpoint

The system SHALL expose `POST /api/1/meetings:instant` accepting a body with a
required `title`, a required `description`, a required `issueLink` (`issueId`,
`issueKey`, `projectKey`), a required `settings` object (`admissionPolicy`,
`maxParticipants` in [2..100], `allowScreenShare`, `chatEnabled`,
`allowMicrophone`, `allowVideo`), a required `host` object (`displayName`,
`deviceId`, and an optional `avatarUrl`), and an optional `invitees` array (each
with a required `email`, a required `accountId`, and a required `displayName`).
On success it SHALL return `201 Created` with a `Location` header referencing
the new meeting and a body containing the meeting snapshot (including non-null
`title`, `description`, and `issueLink`) and the host's LiveKit access token.
The meeting snapshot SHALL NOT include the tenant identifier.

#### Scenario: Successful instant creation returns snapshot and host token

- **WHEN** a valid request is submitted with resolved host identity and tenant
- **THEN** the response is `201 Created`, the body contains the meeting snapshot
  (id, host, shortCode, type INSTANT, status LIVE, title, description,
  issueLink, settings, createdAt) and a `livekit` object with a non-empty
  `token` and the `roomName` `meeting-<meetingId>`, and the tenant identifier is
  absent from the body. The fields `title`, `description`, and `issueLink` are
  never null.

#### Scenario: Missing required settings is a validation error

- **WHEN** a request omits the `settings` object or a required settings field
- **THEN** the response is `400` Problem Details with code `VALIDATION_ERROR`
  and an `errors` entry for the missing field, and no meeting is created

#### Scenario: Missing host identity fields is a validation error

- **WHEN** a request omits `host.displayName` or `host.deviceId`
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

### Requirement: Instant meeting lifecycle

Creating an instant meeting SHALL produce a `Meeting` of type `INSTANT` with a
unique join `shortCode`, SHALL immediately transition it to `LIVE`, and SHALL
persist meeting state and any invitees within a single database transaction.
Host participation logging is deferred to the `participant_joined` LiveKit
webhook (out of scope for this capability).

#### Scenario: Meeting is created live

- **WHEN** an instant meeting is created successfully
- **THEN** the persisted meeting has type `INSTANT` and status `LIVE`, and no
  `ParticipationLog` is created at creation time (host participation is recorded
  later via the LiveKit webhook)

#### Scenario: Short code uniqueness is enforced with retry

- **WHEN** a generated short code collides with an existing meeting's code
- **THEN** the system retries generation and, only after exhausting its bounded
  attempts, fails with a short-code-exhausted error without creating a meeting

#### Scenario: Persistence failure creates nothing

- **WHEN** persisting the meeting or invitees fails
- **THEN** the transaction rolls back and no meeting, invitee, or outbox row is
  left behind

### Requirement: Invitee registration with invite tokens

When the request includes invitees, the system SHALL create one `MeetingInvitee`
per entry using the frontend-resolved `email`, `accountId`, and `displayName`
(all required), and SHALL generate a single-use invite token per invitee,
storing only the token's SHA-256 hash. The raw invite tokens SHALL NOT be
persisted and SHALL be carried only in the invitations event (embedded directly
in each `InviteeInfo` entry) for downstream delivery. Sending invitation emails
is out of scope for this capability.

#### Scenario: Invitees persisted with hashed tokens

- **WHEN** an instant meeting is created with two invitees
- **THEN** two `MeetingInvitee` rows are persisted in PENDING status, each with
  a stored token hash and no stored raw token

#### Scenario: No invitees produces no invitations event

- **WHEN** an instant meeting is created with an empty or absent invitees list
- **THEN** no `MeetingInvitee` row is created and no invitations event is
  enqueued, while the meeting is still created LIVE

### Requirement: Instant meeting event publication

Creating an instant meeting SHALL enqueue its domain events to the transactional
outbox within the creation transaction so they are durable if and only if the
creation commits. The system SHALL enqueue a meeting-created event (carrying a
full aggregate snapshot) and a meeting-started event (also carrying a full
aggregate snapshot plus the LiveKit room name) for every instant meeting, and a
meeting-invitations-sent event carrying the invite token embedded in each
invitee entry only when invitees are present. Each event SHALL be published to
Kafka as a CloudEvent by the shared outbox relay, and the domain SHALL remain
free of protocol-buffer and messaging types.

#### Scenario: Base events enqueued atomically

- **WHEN** an instant meeting is created and its transaction commits
- **THEN** the outbox contains a meeting-created row and a meeting-started row
  for that meeting, each unpublished, with the meeting id as aggregate id

#### Scenario: Invitations event enqueued only with invitees

- **WHEN** an instant meeting is created with invitees
- **THEN** the outbox additionally contains a meeting-invitations-sent row whose
  payload carries each invitee's raw invite token embedded in their entry

#### Scenario: Rolled-back creation enqueues no events

- **WHEN** the creation transaction is rolled back before commit
- **THEN** no meeting-created, meeting-started, or meeting-invitations-sent
  outbox row exists for that meeting

#### Scenario: Events relayed to Kafka as CloudEvents

- **WHEN** the shared outbox relay processes the enqueued rows
- **THEN** each event is published to its topic as a CloudEvent keyed by the
  tenant identifier and the row is marked published

### Requirement: Host LiveKit access token issuance

The system SHALL issue a LiveKit access token granting HOST permissions for the
room `meeting-<meetingId>`, using the host's LiveKit identity and display name,
and SHALL set the host's `role` and (when provided) `avatarUrl` as real LiveKit
participant attributes on the token. The token SHALL be returned in the creation
response. If the LiveKit token cannot be generated, the creation SHALL fail and
roll back rather than returning a meeting the host cannot join.

#### Scenario: Host token issued for the meeting room

- **WHEN** an instant meeting is created successfully
- **THEN** the response `livekit.token` is a non-empty token scoped to room
  `meeting-<meetingId>` for the host identity with host-level permissions

#### Scenario: Host avatar attached as participant attribute

- **WHEN** a request includes `host.avatarUrl`
- **THEN** the issued LiveKit token carries a participant attribute `avatarUrl`
  with that value and a participant attribute `role` = `HOST`

#### Scenario: Host avatar omitted leaves no avatarUrl attribute

- **WHEN** a request omits `host.avatarUrl` or sends null
- **THEN** the issued LiveKit token carries a participant attribute `role` =
  `HOST` but no `avatarUrl` attribute

#### Scenario: LiveKit unavailable fails creation

- **WHEN** the LiveKit token cannot be generated during creation
- **THEN** the response is a Problem Details error indicating LiveKit is
  unavailable and the transaction rolls back so no meeting is persisted
