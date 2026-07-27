# remove-meeting-invitees Specification

## Purpose

Defines the endpoint for removing one or more invitees from an existing meeting
by invitee id. Only the meeting host may remove invitees, and only while the
meeting is in `SCHEDULED` status. The endpoint validates inputs, rejects unknown
ids atomically, and publishes an invitee-removal event on success.

## Requirements

### Requirement: Host-only invitee removal endpoint

The system SHALL expose `POST /api/1/meetings/{id}/invitees:batchDelete` for
removing one or more invitees from a meeting by invitee id. The acting account
SHALL be resolved from the configured account header, the tenant SHALL be
resolved from the tenant context, and only the meeting host SHALL be authorized
to remove invitees. The request body SHALL use the shape
`{ "inviteeIds": [ "<uuid>" ] }`. A successful call SHALL return `200 OK` with
the full snapshot of the invitees removed by that call.

#### Scenario: Host removes invitees

- **WHEN** the host sends a valid
  `POST /api/1/meetings/{id}/invitees:batchDelete` request with the account
  header and one or more invitee ids that are currently active
- **THEN** the system soft-deletes each referenced invitee and returns `200 OK`
  with the snapshot of the removed invitees, each carrying `id`, `accountId`,
  `email`, `displayName`, `role`, `status`, `invitedAt`, and `respondedAt`

#### Scenario: Non-host removal is rejected

- **WHEN** an account that is not the meeting host sends an invitee-removal
  request
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and HTTP `403`, removes no invitee, and publishes no
  event

#### Scenario: Missing account identity is rejected

- **WHEN** the request does not contain the configured account header
- **THEN** the system returns `400` Problem Details and removes no invitee

#### Scenario: Unknown meeting is rejected

- **WHEN** the request references a meeting id that does not exist in the tenant
- **THEN** the system returns a `404` Problem Details response with code
  `MEETING_NOT_FOUND` and publishes no event

### Requirement: Status-gated invitee removal

The system SHALL allow invitee removal only when the meeting status is
`SCHEDULED`. The system SHALL reject invitee removal for meetings whose status
is `RUNNING`, `COMPLETED`, or `CANCELED` with an invalid-status Problem Details
response, and SHALL not remove any invitee or publish any event.

#### Scenario: Scheduled meeting accepts invitee removal

- **WHEN** the host removes invitees from a `SCHEDULED` meeting
- **THEN** the invitees are soft-deleted and the change is persisted

#### Scenario: Non-scheduled meeting rejects invitee removal

- **WHEN** the host attempts to remove invitees from a `RUNNING`, `COMPLETED`,
  or `CANCELED` meeting
- **THEN** the request is rejected with an invalid-status Problem Details
  response, no invitee is removed, and no event is published

### Requirement: Input validation for invitee removal

The system SHALL require the request to contain at least one invitee id. An
empty `inviteeIds` array SHALL be rejected with `400` Problem Details carrying
`VALIDATION_ERROR`, and no invitee SHALL be removed.

#### Scenario: Empty invitee id list is rejected

- **WHEN** the request contains an empty `inviteeIds` array
- **THEN** the system returns `400` validation Problem Details and removes no
  invitee

### Requirement: Atomic removal by invitee id

The system SHALL resolve every submitted invitee id against the meeting's
current active invitees. If any submitted id does not correspond to an active
invitee of the meeting — because it does not exist, belongs to another meeting,
or was already soft-deleted — the system SHALL reject the entire request
atomically with a `404` Problem Details response carrying the
`INVITEE_NOT_FOUND` code, SHALL remove no invitee, and SHALL publish no event.

#### Scenario: Removing an unknown invitee id fails the whole batch

- **WHEN** the request contains an invitee id that does not correspond to an
  active invitee of the meeting, alongside valid ids
- **THEN** the system returns `404` Problem Details with code
  `INVITEE_NOT_FOUND`, removes none of the submitted invitees, and publishes no
  event

#### Scenario: Removing an already-removed invitee fails the whole batch

- **WHEN** the request contains an invitee id whose invitee was already
  soft-deleted
- **THEN** the system returns `404` Problem Details with code
  `INVITEE_NOT_FOUND` and removes no invitee

### Requirement: Invitee-removal event

The system SHALL publish `meet.meeting.invitations.deleted` when at least one
invitee is removed. The event SHALL carry the meeting identity and context
together with the list of removed invitees, and SHALL be enqueued atomically
with the invitee changes.

#### Scenario: Successful removal publishes the deleted event

- **WHEN** a request removes one or more invitees
- **THEN** exactly one `meet.meeting.invitations.deleted` event carrying only
  the removed invitees is enqueued atomically with the change

#### Scenario: Failed removal publishes no event

- **WHEN** authorization, status, validation, or id-resolution checks reject the
  request
- **THEN** no invitee change and no invitee event is persisted
