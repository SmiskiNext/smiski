# delete-meeting

## Purpose

TBD - created by archiving change delete-meeting. Update Purpose after archive.

## Requirements

### Requirement: Host-only single meeting soft-delete endpoint

The system SHALL expose `DELETE /api/1/meetings/{id}` for soft-deleting a single
meeting. The endpoint SHALL require the `edit-meeting` project permission — if
the caller's permission context does not contain `edit-meeting`, the endpoint
SHALL reject the request with a `403` Problem Details response and code
`NOT_AUTHORIZED` before executing the use case. The acting account SHALL be
resolved from the configured account header, the tenant SHALL be resolved from
the tenant context, and only the meeting host SHALL be authorized to delete the
meeting. A successful deletion SHALL mark the meeting soft-deleted and return
`200 OK` with the full snapshot of the deleted meeting, including the deletion
timestamp and deleting account.

#### Scenario: Host deletes an eligible meeting

- **WHEN** the host sends `DELETE /api/1/meetings/{id}` with the account header
  for a meeting whose status is `SCHEDULED`, `COMPLETED`, or `CANCELED` and has
  `edit-meeting` permission
- **THEN** the system records the deletion timestamp and the deleting account,
  the meeting no longer appears in tenant meeting lists, and the response is
  `200 OK` carrying the deleted meeting snapshot (including the deletion
  timestamp and deleting account)

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends `DELETE /api/1/meetings/{id}`
- **THEN** the response is `403` Problem Details with code `NOT_AUTHORIZED` and
  the meeting is not deleted

#### Scenario: Non-host deletion is rejected

- **WHEN** an account that is not the meeting host sends a delete request even
  with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and the meeting is not deleted

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
