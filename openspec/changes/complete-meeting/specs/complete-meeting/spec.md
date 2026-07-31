## ADDED Requirements

### Requirement: Host-only manual meeting completion endpoint

The system SHALL expose `POST /api/1/meetings/{id}:end` to complete a single
`RUNNING` meeting. The acting account SHALL be resolved from the configured
account header and the tenant SHALL be resolved from the tenant context. Only
the meeting host SHALL be authorized to end the meeting. A successful completion
SHALL transition the meeting status from `RUNNING` to `COMPLETED`, set the
meeting end time to the completion moment, close every still-active
participation log for the meeting, publish a `MeetingCompletedEvent` through the
transactional outbox, and return `200 OK` with the full snapshot of the
completed meeting.

No request body is required.

#### Scenario: Host ends a RUNNING meeting

- **WHEN** the host sends `POST /api/1/meetings/{id}:end` with the account
  header for a meeting whose status is `RUNNING`
- **THEN** the system transitions the meeting to `COMPLETED`, sets its end time
  to the completion moment, closes every still-active participation log,
  publishes one `MeetingCompletedEvent`, and returns `200 OK` with the full
  completed meeting snapshot

#### Scenario: Non-host completion is rejected

- **WHEN** an authenticated account that is not the meeting host sends an end
  request
- **THEN** the system returns `403` Problem Details with code `NOT_AUTHORIZED`,
  the meeting remains unchanged, and no event is published

#### Scenario: Missing account header is rejected

- **WHEN** the end request does not carry the configured account header
- **THEN** the system returns `400` Problem Details with code `VALIDATION_ERROR`
  and does not change any meeting

#### Scenario: Unknown or soft-deleted meeting is rejected

- **WHEN** the end request references an ID that does not exist or has been
  soft-deleted in the tenant
- **THEN** the system returns `404` Problem Details with code
  `MEETING_NOT_FOUND` and publishes no event

### Requirement: Only RUNNING meetings can be manually completed

The system SHALL reject manual completion of any meeting whose status is not
`RUNNING`. Attempting to end a `SCHEDULED`, already `COMPLETED`, or `CANCELED`
meeting SHALL return a conflict response. No state change and no event SHALL be
produced on rejection.

#### Scenario: Ending a SCHEDULED meeting is rejected

- **WHEN** the host sends an end request for a meeting whose status is
  `SCHEDULED`
- **THEN** the system returns `409` Problem Details with code
  `INVALID_STATUS_TRANSITION` and the meeting remains unchanged with no event
  published

#### Scenario: Ending an already-COMPLETED meeting is rejected

- **WHEN** the host sends an end request for a meeting whose status is already
  `COMPLETED`
- **THEN** the system returns `409` Problem Details with code
  `INVALID_STATUS_TRANSITION` and the meeting remains unchanged with no event
  published

#### Scenario: Ending a CANCELED meeting is rejected

- **WHEN** the host sends an end request for a meeting whose status is
  `CANCELED`
- **THEN** the system returns `409` Problem Details with code
  `INVALID_STATUS_TRANSITION` and the meeting remains unchanged with no event
  published

### Requirement: Active participation logs are closed on completion

On a successful completion the system SHALL close every still-active
participation log for the meeting by setting its left-at timestamp to the
completion moment and its close reason to `LEFT`, matching the closure behavior
already performed by the `room_finished` webhook path. Closure SHALL occur in
the same transaction as the status change.

#### Scenario: Active sessions are closed at completion time

- **WHEN** the host ends a `RUNNING` meeting that has active participation logs
- **THEN** every still-active participation log for that meeting is closed with
  close reason `LEFT` and a left-at timestamp of the completion moment, in the
  same transaction as the status change

#### Scenario: Completion with no active sessions still succeeds

- **WHEN** the host ends a `RUNNING` meeting that has no active participation
  logs
- **THEN** the meeting transitions to `COMPLETED` and the completion succeeds
  without error

### Requirement: Best-effort LiveKit room deletion

On a successful completion the system SHALL instruct the LiveKit media server to
delete the meeting room so any still-connected participants are disconnected. A
failure of the room deletion SHALL be recorded but SHALL NOT roll back the
completed status or the published event; the transition is authoritative in the
application regardless of the media server outcome.

#### Scenario: Room deletion is requested after completion

- **WHEN** the host successfully ends a `RUNNING` meeting
- **THEN** the system requests deletion of the corresponding LiveKit room

#### Scenario: Room deletion failure does not roll back completion

- **WHEN** the meeting is transitioned to `COMPLETED` but the LiveKit room
  deletion request fails
- **THEN** the meeting remains `COMPLETED`, the `MeetingCompletedEvent` is still
  published, the failure is recorded, and the endpoint returns `200 OK`

### Requirement: Manual completion is idempotent against the later room-finished webhook

The system SHALL ensure that, because manual completion transitions the meeting
to `COMPLETED` synchronously, the subsequent LiveKit room-finished webhook for
the same meeting is a no-op through the existing meeting status transition
guard. The webhook SHALL be acknowledged without error and SHALL NOT publish a
duplicate `MeetingCompletedEvent` or re-close already-closed participation logs.

#### Scenario: room_finished after manual completion is a no-op

- **WHEN** a `room_finished` webhook arrives for a meeting that was already
  manually completed via the `:end` endpoint
- **THEN** no meeting or participation-log state changes, no duplicate
  `MeetingCompletedEvent` is published, and the webhook is acknowledged without
  error

### Requirement: Meeting completion event

The system SHALL publish a `MeetingCompletedEvent` for each successfully
completed meeting through the transactional outbox so the event is persisted
atomically with the completion. The event SHALL carry the meeting ID, tenant ID,
host ID, and the completion timestamp, and SHALL be serialized via the existing
`MeetingCompleted` protobuf message and published to the
`meet.meeting.completed` Kafka topic. No event SHALL be published when a
completion is rejected.

#### Scenario: Successful completion publishes an event atomically

- **WHEN** a meeting is successfully completed via the `:end` endpoint
- **THEN** exactly one `MeetingCompletedEvent` is persisted in the same
  transaction as the status change and later relayed to Kafka by the outbox
  relay

#### Scenario: Rejected completion publishes no event

- **WHEN** a completion attempt is rejected for authorization, not-found, or
  invalid-status-transition reasons
- **THEN** no `MeetingCompletedEvent` is persisted or published
