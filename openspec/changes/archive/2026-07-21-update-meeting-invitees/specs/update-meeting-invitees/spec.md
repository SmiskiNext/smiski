## ADDED Requirements

### Requirement: Host-only invitee replacement endpoint

The system SHALL expose `PUT /api/1/meetings/{id}/invitees` for replacing the
full invitee list of a meeting. The acting account SHALL be resolved from the
configured account header, the tenant SHALL be resolved from the tenant context,
and only the meeting host SHALL be authorized to modify invitees. The request
and response bodies SHALL both use the shape `{ "invitees": [ { ... } ] }`. A
successful call SHALL return `200 OK` with the current active invitee list after
synchronization.

#### Scenario: Host replaces the invitee list

- **WHEN** the host sends a valid `PUT /api/1/meetings/{id}/invitees` request
  with the account header
- **THEN** the system synchronizes the invitees and returns `200 OK` with the
  current active invitee list

#### Scenario: Non-host modification is rejected

- **WHEN** an account that is not the meeting host sends an invitee replacement
  request
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code, does not change any invitee, and publishes no event

#### Scenario: Missing account identity is rejected

- **WHEN** the request does not contain the configured account header
- **THEN** the system returns `400` Problem Details and does not change any
  invitee

#### Scenario: Unknown meeting is rejected

- **WHEN** the request references a meeting ID that does not exist in the tenant
- **THEN** the system returns a meeting-not-found Problem Details response and
  publishes no event

### Requirement: Status-gated invitee management

The system SHALL allow invitee replacement only when the meeting status is
`SCHEDULED`. The system SHALL reject invitee replacement for meetings whose
status is `RUNNING`, `COMPLETED`, or `CANCELED` with an invalid-status Problem
Details response, and SHALL not change any invitee or publish any event.

#### Scenario: Scheduled meeting accepts invitee replacement

- **WHEN** the host replaces invitees on a `SCHEDULED` meeting
- **THEN** the change is applied and persisted

#### Scenario: Running meeting rejects invitee replacement

- **WHEN** the host attempts to replace invitees on a `RUNNING` meeting
- **THEN** the request is rejected, no invitee is changed, and no event is
  published

#### Scenario: Completed or canceled meeting rejects invitee replacement

- **WHEN** the host attempts to replace invitees on a `COMPLETED` or `CANCELED`
  meeting
- **THEN** the request is rejected and the invitee list remains unchanged

### Requirement: Input validation for invitee replacement

The system SHALL validate each submitted invitee with the same constraints as
meeting creation: a non-blank valid `email`, a non-blank `accountId`, and a
non-blank `displayName`. The system SHALL reject a request that contains two or
more invitees with the same `accountId`. Invalid requests SHALL return `400`
Problem Details with `VALIDATION_ERROR` and SHALL not change any invitee.

#### Scenario: Invalid invitee field is rejected

- **WHEN** an invitee entry has a blank or malformed `email`, a blank
  `accountId`, or a blank `displayName`
- **THEN** the system returns `400` validation Problem Details and persists no
  part of the change

#### Scenario: Duplicate accountId is rejected

- **WHEN** the request contains two invitee entries with the same `accountId`
- **THEN** the system returns `400` validation Problem Details and persists no
  part of the change

#### Scenario: Empty invitee list removes all invitees

- **WHEN** the request contains an empty `invitees` array and the meeting
  currently has active invitees
- **THEN** the system removes all current active invitees and returns `200 OK`
  with an empty invitee list

### Requirement: AccountId-based invitee diffing

The system SHALL compare the submitted invitees against the current active
invitees of the meeting using `accountId` as the identity key. An invitee
present in the request but not among the current active invitees SHALL be
created as a new `NEEDS_ACTION` invitation. An invitee present in both whose
`displayName` differs SHALL be updated in place. An invitee present among the
current active invitees but absent from the request SHALL be removed via soft
delete. The `email`, `role`, and `rsvp` of an existing invitee SHALL NOT change
through this endpoint.

#### Scenario: New invitee is created

- **WHEN** the request contains an `accountId` that has no active invitee for
  the meeting
- **THEN** a new invitee is created for that account with status `NEEDS_ACTION`

#### Scenario: Existing invitee display name is updated

- **WHEN** the request contains an `accountId` that already has an active
  invitee and supplies a different `displayName`
- **THEN** that invitee's `displayName` is updated and its `email`, `role`,
  `rsvp`, and response status are preserved

#### Scenario: Absent invitee is removed

- **WHEN** an account currently has an active invitee but is not present in the
  request
- **THEN** that invitee is soft-deleted and no longer appears in the active
  invitee list

#### Scenario: Unchanged invitee is left intact

- **WHEN** the request contains an `accountId` whose active invitee has the same
  `displayName`
- **THEN** that invitee is neither updated nor removed and remains unchanged

#### Scenario: Re-adding a previously removed account creates a fresh invitee

- **WHEN** an account whose only prior invitee was soft-deleted appears again in
  the request
- **THEN** a new active invitee is created for that account with status
  `NEEDS_ACTION`

### Requirement: Change-sensitive batch invitee events

The system SHALL publish `meet.meeting.invitations.created` when at least one
invitee is created, `meet.meeting.invitations.updated` when at least one invitee
is updated, and `meet.meeting.invitations.deleted` when at least one invitee is
removed. Each event SHALL carry the meeting identity and context together with
the list of affected invitees. Each event SHALL be published only when its group
is non-empty, and SHALL be enqueued atomically with the invitee changes. A
request whose effective diff produces no created, updated, or removed invitee
SHALL publish no event.

#### Scenario: Created group publishes the created event

- **WHEN** a synchronization creates one or more invitees
- **THEN** exactly one `meet.meeting.invitations.created` event carrying only
  the created invitees is enqueued atomically with the change

#### Scenario: Updated group publishes the updated event

- **WHEN** a synchronization updates one or more invitees' display names
- **THEN** exactly one `meet.meeting.invitations.updated` event carrying only
  the updated invitees is enqueued atomically with the change

#### Scenario: Removed group publishes the deleted event

- **WHEN** a synchronization removes one or more invitees
- **THEN** exactly one `meet.meeting.invitations.deleted` event carrying only
  the removed invitees is enqueued atomically with the change

#### Scenario: Mixed change publishes each non-empty group once

- **WHEN** a single synchronization creates, updates, and removes invitees
- **THEN** one created event, one updated event, and one deleted event are
  enqueued in the same transaction, each carrying only its respective invitees

#### Scenario: No-op synchronization publishes no event

- **WHEN** the submitted list matches the current active invitees with no
  effective change
- **THEN** the system returns the unchanged invitee list and publishes no event

#### Scenario: Failed synchronization publishes no event

- **WHEN** authorization, status, or validation checks reject the request
- **THEN** no invitee change and no invitee event is persisted
