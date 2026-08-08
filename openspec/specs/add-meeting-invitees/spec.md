# add-meeting-invitees Specification

## Purpose

Defines the endpoint for adding one or more new invitees to an existing meeting.
Only the meeting host may add invitees, and only while the meeting is in
`SCHEDULED` or `RUNNING` status. The endpoint validates inputs, rejects
duplicates atomically, and publishes an invitee-creation event on success.

## Requirements

### Requirement: Host-only invitee creation endpoint

The system SHALL expose `POST /api/1/meetings/{id}/invitees` for adding one or
more new invitees to a meeting. The endpoint SHALL require the `edit-meeting`
project permission — if the caller's permission context does not contain
`edit-meeting`, the endpoint SHALL reject the request with a `403` Problem
Details response and code `NOT_AUTHORIZED` before executing the use case. The
acting account SHALL be resolved from the configured account header, the tenant
SHALL be resolved from the tenant context, and only the meeting host SHALL be
authorized to add invitees. The request body SHALL use the shape
`{ "invitees": [ { "email", "accountId", "displayName" } ] }`. A successful call
SHALL return `200 OK` with the full snapshot of the invitees created by that
call.

#### Scenario: Host adds new invitees

- **WHEN** the host sends a valid `POST /api/1/meetings/{id}/invitees` request
  with the account header and one or more invitees that are not yet active and
  has `edit-meeting` permission
- **THEN** the system creates each invitee with status `NEEDS_ACTION` and
  returns `200 OK` with the snapshot of the created invitees, each carrying
  `id`, `accountId`, `email`, `displayName`, `role`, `status`, `invitedAt`, and
  `respondedAt`

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends
  `POST /api/1/meetings/{id}/invitees`
- **THEN** the response is `403` Problem Details with code `NOT_AUTHORIZED` and
  no invitees are added

#### Scenario: Non-host invitee addition is rejected

- **WHEN** an account that is not the meeting host sends an add-invitees request
  even with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and no invitees are created

### Requirement: Status-gated invitee creation

The system SHALL allow invitee creation when the meeting status is `SCHEDULED`
or `RUNNING`. The system SHALL reject invitee creation for meetings whose status
is `COMPLETED` or `CANCELED` with an invalid-status Problem Details response,
and SHALL not create any invitee or publish any event.

#### Scenario: Scheduled meeting accepts invitee creation

- **WHEN** the host adds invitees to a `SCHEDULED` meeting
- **THEN** the invitees are created and persisted

#### Scenario: Running meeting accepts invitee creation

- **WHEN** the host adds invitees to a `RUNNING` meeting
- **THEN** the invitees are created and persisted and the meeting remains
  `RUNNING`

#### Scenario: Completed or canceled meeting rejects invitee creation

- **WHEN** the host attempts to add invitees to a `COMPLETED` or `CANCELED`
  meeting
- **THEN** the request is rejected with an invalid-status Problem Details
  response, no invitee is created, and no event is published

### Requirement: Input validation for invitee creation

The system SHALL validate each submitted invitee with the same constraints as
meeting creation: a non-blank valid `email`, a non-blank `accountId`, and a
non-blank `displayName`. The request SHALL contain at least one invitee. The
system SHALL reject a request that contains two or more invitees with the same
`accountId`. Invalid requests SHALL return `400` Problem Details with
`VALIDATION_ERROR` and SHALL not create any invitee.

#### Scenario: Invalid invitee field is rejected

- **WHEN** an invitee entry has a blank or malformed `email`, a blank
  `accountId`, or a blank `displayName`
- **THEN** the system returns `400` validation Problem Details and persists no
  part of the request

#### Scenario: Empty invitee list is rejected

- **WHEN** the request contains an empty `invitees` array
- **THEN** the system returns `400` validation Problem Details and creates no
  invitee

#### Scenario: In-request duplicate accountId is rejected

- **WHEN** the request contains two invitee entries with the same `accountId`
- **THEN** the system returns `400` validation Problem Details and persists no
  part of the request

### Requirement: Atomic rejection of already-active accounts

The system SHALL compare each submitted invitee against the meeting's current
active invitees using `accountId` as the identity key. If any submitted
`accountId` already has an active invitee for the meeting, the system SHALL
reject the entire request atomically with a `409 CONFLICT` Problem Details
response carrying the `INVITEE_ALREADY_EXISTS` code, SHALL create no invitee,
and SHALL publish no event. An `accountId` whose only prior invitee was
soft-deleted SHALL be treated as not active and SHALL be created as a fresh
invitee.

#### Scenario: Adding an already-active account fails the whole batch

- **WHEN** the request contains an `accountId` that already has an active
  invitee for the meeting, alongside other new accounts
- **THEN** the system returns `409` Problem Details with code
  `INVITEE_ALREADY_EXISTS`, creates none of the submitted invitees, and
  publishes no event

#### Scenario: Re-adding a previously removed account creates a fresh invitee

- **WHEN** an account whose only prior invitee was soft-deleted is submitted
- **THEN** a new active invitee is created for that account with status
  `NEEDS_ACTION`

### Requirement: Invitee-creation event

The system SHALL publish `meet.meeting.invitations.created` when at least one
invitee is created. The event SHALL carry the meeting identity and context
together with the list of created invitees, and SHALL be enqueued atomically
with the invitee changes.

#### Scenario: Successful creation publishes the created event

- **WHEN** a request creates one or more invitees
- **THEN** exactly one `meet.meeting.invitations.created` event carrying only
  the created invitees is enqueued atomically with the change

#### Scenario: Failed creation publishes no event

- **WHEN** authorization, status, validation, or duplicate-account checks reject
  the request
- **THEN** no invitee change and no invitee event is persisted
