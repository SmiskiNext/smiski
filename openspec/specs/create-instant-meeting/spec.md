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

### Requirement: Host LiveKit access token issuance

The system SHALL issue a LiveKit access token granting HOST permissions for the
room `meeting-<meetingId>`, using the host's LiveKit identity and display name,
and SHALL set the host's `role` and (when provided) `avatarUrl` as real LiveKit
participant attributes on the token. The token SHALL additionally carry a
LiveKit room configuration whose room name is `meeting-<meetingId>` and whose
room metadata carries the owning tenant identifier, so that the LiveKit room
created on first join exposes the tenant in its metadata for later
room/participant webhooks. The token SHALL be returned in the creation response.
The tenant identifier SHALL NOT appear in the creation response body. If the
LiveKit token cannot be generated, the creation SHALL fail and roll back rather
than returning a meeting the host cannot join.

#### Scenario: Host token issued for the meeting room

- **WHEN** an instant meeting is created successfully
- **THEN** the response `livekit.token` is a non-empty token scoped to room
  `meeting-<meetingId>` for the host identity with host-level permissions

#### Scenario: Host token embeds tenant in room metadata

- **WHEN** an instant meeting is created successfully for a given tenant
- **THEN** the issued LiveKit token carries a room configuration for room
  `meeting-<meetingId>` whose metadata carries that tenant identifier, and the
  tenant identifier is absent from the creation response body

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

### Requirement: App sources instant invitees from workspace users

The Forge app SHALL populate the instant-meeting invitee list from Jira site
(workspace) users rather than only project-assignable users. Each invitee the
app submits to `POST /api/1/meetings:instant` SHALL carry the `email`,
`accountId`, and `displayName` resolved from the selected workspace user, so the
request satisfies the endpoint's required invitee shape.

#### Scenario: Workspace-sourced invitees satisfy the endpoint contract

- **WHEN** the app creates an instant meeting with invitees selected from
  workspace users
- **THEN** each invitee in the request body has a non-blank `email`,
  `accountId`, and `displayName`

#### Scenario: Instant meeting without invitees omits the list

- **WHEN** the host creates an instant meeting without selecting invitees
- **THEN** the app sends the request with an empty or absent invitees array and
  the meeting is still created

### Requirement: App creates instant meetings from dashboard and Issue Panel

The Forge app SHALL let the host create an instant meeting from both the
project-page dashboard and the Issue Panel, using one shared instant-meeting
form. From the Issue Panel, the create action SHALL first apply the existing
active-meeting (host conflict) check and then open the shared form; it SHALL NOT
create the meeting without showing the form. Both entry points SHALL send the
same instant-create request contract to the backend.

#### Scenario: Create from the dashboard

- **WHEN** the host starts an instant meeting from the dashboard
- **THEN** the shared instant-meeting form is shown and, on submit, an
  instant-create request is sent to the backend

#### Scenario: Create from the Issue Panel opens the form

- **WHEN** the host triggers "Start instant" from the Issue Panel and no host
  conflict blocks it
- **THEN** the shared instant-meeting form opens prefilled for the current issue
  instead of creating a meeting immediately

#### Scenario: Host conflict still guards the Issue Panel entry

- **WHEN** the host triggers "Start instant" from the Issue Panel while already
  hosting a running meeting
- **THEN** the active-meeting warning is shown before the instant-meeting form
