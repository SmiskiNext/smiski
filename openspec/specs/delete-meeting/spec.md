# delete-meeting

## Requirements

### Requirement: Host-only single meeting soft-delete endpoint

The system SHALL expose `DELETE /api/1/meetings/{id}` for soft-deleting a single
meeting. The acting account SHALL be resolved from the configured account
header, the tenant SHALL be resolved from the tenant context, and only the
meeting host SHALL be authorized to delete the meeting. A successful deletion
SHALL mark the meeting soft-deleted and return `200 OK` with the full snapshot
of the deleted meeting, including the deletion timestamp and deleting account.

#### Scenario: Host deletes an eligible meeting

- **WHEN** the host sends `DELETE /api/1/meetings/{id}` with the account header
  for a meeting whose status is `SCHEDULED`, `COMPLETED`, or `CANCELED`
- **THEN** the system records the deletion timestamp and the deleting account,
  the meeting no longer appears in tenant meeting lists, and the response is
  `200 OK` carrying the deleted meeting snapshot (including the deletion
  timestamp and deleting account)

#### Scenario: Non-host deletion is rejected

- **WHEN** an account that is not the meeting host sends a delete request
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code, the meeting remains unchanged, and no event is
  published

#### Scenario: Missing account identity is rejected

- **WHEN** the delete request does not contain the configured account header
- **THEN** the system returns `400` Problem Details and does not change any
  meeting

#### Scenario: Unknown meeting is rejected

- **WHEN** the delete request references an ID that does not exist in the tenant
- **THEN** the system returns a meeting-not-found Problem Details response with
  status `404` and publishes no event

#### Scenario: Already-deleted meeting is treated as not found

- **WHEN** the delete request references a meeting that has already been
  soft-deleted
- **THEN** the system returns a meeting-not-found Problem Details response with
  status `404` and publishes no event

### Requirement: Running meetings cannot be deleted

The system SHALL reject deletion of any meeting whose status is `RUNNING`. The
rejection SHALL use a dedicated conflict-category domain error and SHALL leave
the meeting unchanged with no event published. A meeting must transition out of
`RUNNING` before it can be deleted.

#### Scenario: Deleting a running meeting is rejected

- **WHEN** the host sends a delete request for a meeting whose status is
  `RUNNING`
- **THEN** the system returns a Problem Details response with status `409` and a
  machine-readable code indicating the meeting is running, and the meeting
  remains unchanged with no deletion event

### Requirement: Atomic batch soft-delete action endpoint

The system SHALL expose `POST /api/1/meetings:batchDelete` accepting a request
body carrying a non-empty list of meeting identifiers. The acting account and
tenant SHALL be resolved the same way as the single-delete endpoint. The batch
SHALL be all-or-nothing: the system SHALL validate every identifier before
mutating any meeting, and if any identifier fails authorization, is not found,
is already deleted, or references a `RUNNING` meeting, the system SHALL reject
the entire request, delete no meeting, and publish no event. A fully successful
batch SHALL soft-delete every listed meeting in a single transaction and return
`200 OK` with the full snapshots of every deleted meeting.

#### Scenario: All identifiers eligible

- **WHEN** the host sends a batch delete request whose identifiers all reference
  host-owned meetings that are not `RUNNING` and not already deleted
- **THEN** the system soft-deletes every listed meeting in one transaction,
  publishes one deletion event per meeting, and returns `200 OK` carrying the
  snapshots of every deleted meeting

#### Scenario: One running meeting fails the whole batch

- **WHEN** a batch delete request contains at least one identifier whose meeting
  is `RUNNING`
- **THEN** the system returns a Problem Details response indicating the meeting
  is running, no meeting in the batch is deleted, and no deletion event is
  published

#### Scenario: One unknown or already-deleted identifier fails the whole batch

- **WHEN** a batch delete request contains an identifier that does not exist or
  has already been soft-deleted
- **THEN** the system returns a meeting-not-found Problem Details response, no
  meeting in the batch is deleted, and no deletion event is published

#### Scenario: One non-host meeting fails the whole batch

- **WHEN** a batch delete request contains an identifier for a meeting the
  acting account does not host
- **THEN** the system returns an authorization Problem Details response, no
  meeting in the batch is deleted, and no deletion event is published

#### Scenario: Empty identifier list is rejected

- **WHEN** a batch delete request contains an empty or missing identifier list
- **THEN** the system returns `400` validation Problem Details and deletes no
  meeting

### Requirement: Meeting deletion event

The system SHALL publish a `MeetingDeletedEvent` for each successfully
soft-deleted meeting through the transactional outbox so the event is persisted
atomically with the deletion. Each event SHALL carry a full meeting snapshot
(reusing the shared `MeetingSnapshot` structure, consistent with the created and
started events) plus the deleting account and deletion timestamp. No event SHALL
be published when a deletion is rejected.

#### Scenario: Successful deletion publishes an event

- **WHEN** a meeting is successfully soft-deleted
- **THEN** exactly one `MeetingDeletedEvent` is persisted in the same
  transaction as the deletion and later published to its Kafka topic

#### Scenario: Rejected deletion publishes no event

- **WHEN** a deletion is rejected for authorization, not-found, already-deleted,
  or running-meeting reasons
- **THEN** no `MeetingDeletedEvent` is persisted or published
