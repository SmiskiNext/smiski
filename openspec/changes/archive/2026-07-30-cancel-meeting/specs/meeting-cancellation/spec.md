# meeting-cancellation Specification

## ADDED Requirements

### Requirement: Host-only manual meeting cancellation endpoint

The system SHALL expose `POST /api/1/meetings/{id}:cancel` to cancel a single
SCHEDULED meeting. The acting account SHALL be resolved from the configured
account header and the tenant SHALL be resolved from the tenant context. Only
the meeting host SHALL be authorized to cancel. A successful cancellation SHALL
transition the meeting status from `SCHEDULED` to `CANCELED`, record the reason
as `HOST_CANCELED`, load all active invitees, publish a `MeetingCanceledEvent`
with the invitee list through the transactional outbox, and return `200 OK` with
the full snapshot of the canceled meeting including the reason.

No request body is required; the cancel reason is always `HOST_CANCELED` for
this endpoint.

#### Scenario: Host cancels a SCHEDULED meeting

- **WHEN** the host sends `POST /api/1/meetings/{id}:cancel` with the account
  header for a meeting whose status is `SCHEDULED`
- **THEN** the system transitions the meeting to `CANCELED` with reason
  `HOST_CANCELED`, publishes one `MeetingCanceledEvent` carrying the active
  invitee list, and returns `200 OK` with the full canceled meeting snapshot

#### Scenario: Non-host cancellation is rejected

- **WHEN** an authenticated account that is not the meeting host sends a cancel
  request
- **THEN** the system returns `403` Problem Details with code `NOT_AUTHORIZED`,
  the meeting remains unchanged, and no event is published

#### Scenario: Missing account header is rejected

- **WHEN** the cancel request does not carry the configured account header
- **THEN** the system returns `400` Problem Details with code `VALIDATION_ERROR`
  and does not change any meeting

#### Scenario: Unknown or soft-deleted meeting is rejected

- **WHEN** the cancel request references an ID that does not exist or has been
  soft-deleted in the tenant
- **THEN** the system returns `404` Problem Details with code
  `MEETING_NOT_FOUND` and publishes no event

### Requirement: Only SCHEDULED meetings can be manually canceled

The system SHALL reject manual cancellation of any meeting whose status is not
`SCHEDULED`. Attempting to cancel a `RUNNING`, `COMPLETED`, or already
`CANCELED` meeting SHALL return a conflict response. No state change and no
event SHALL be produced on rejection.

#### Scenario: Canceling a RUNNING meeting is rejected

- **WHEN** the host sends a cancel request for a meeting whose status is
  `RUNNING`
- **THEN** the system returns `409` Problem Details with code
  `INVALID_STATUS_TRANSITION` and the meeting remains unchanged with no event
  published

#### Scenario: Canceling a COMPLETED meeting is rejected

- **WHEN** the host sends a cancel request for a meeting whose status is
  `COMPLETED`
- **THEN** the system returns `409` Problem Details with code
  `INVALID_STATUS_TRANSITION` and the meeting remains unchanged with no event
  published

#### Scenario: Canceling an already-CANCELED meeting is rejected

- **WHEN** the host sends a cancel request for a meeting whose status is already
  `CANCELED`
- **THEN** the system returns `409` Problem Details with code
  `INVALID_STATUS_TRANSITION` and the meeting remains unchanged with no event
  published

### Requirement: No-show automatic cancellation job

The system SHALL run a background job that periodically finds all SCHEDULED
meetings whose scheduled end time has passed (`end_time < now`) and that have
not been soft-deleted, and SHALL cancel each such meeting with reason `NO_SHOW`.
The job SHALL operate across all tenants without a pre-set tenant context and
SHALL set the tenant context per meeting before persisting. The job SHALL
publish one `MeetingCanceledEvent` per canceled meeting through the
transactional outbox. A meeting that was already transitioned to `RUNNING` (room
started via LiveKit webhook) before the job runs SHALL NOT be canceled by the
job, because it will no longer be in `SCHEDULED` status.

#### Scenario: Expired SCHEDULED meeting is auto-canceled

- **WHEN** the no-show job runs and finds a SCHEDULED meeting whose `end_time`
  is in the past and which has not been soft-deleted
- **THEN** the system transitions it to `CANCELED` with reason `NO_SHOW`,
  publishes one `MeetingCanceledEvent` with the active invitee list, and
  persists both changes in the same transaction

#### Scenario: RUNNING or non-SCHEDULED meetings are skipped

- **WHEN** the no-show job runs and a meeting has already transitioned to
  `RUNNING` (or any status other than `SCHEDULED`)
- **THEN** the job skips it and no event is published for that meeting

#### Scenario: Job handles multiple tenants independently

- **WHEN** the no-show job finds eligible meetings belonging to different
  tenants
- **THEN** each meeting is processed with its own tenant context set so that the
  outbox row is attributed to the correct tenant

#### Scenario: Recently ended scheduled meeting with future end_time is not canceled

- **WHEN** the no-show job runs and a SCHEDULED meeting has an `end_time` in the
  future
- **THEN** the job does not cancel that meeting in this run

### Requirement: Meeting cancellation event

The system SHALL publish a `MeetingCanceledEvent` for each successfully canceled
meeting (both manual and automatic) through the transactional outbox so the
event is persisted atomically with the cancellation. Each event SHALL carry the
meeting ID, tenant ID, host ID, cancel reason, meeting title, meeting short
code, scheduled start time, the list of active invitees at cancellation time
(each with account ID, email, display name, status, and invited-at timestamp),
and the cancellation timestamp. The event SHALL be serialized via a dedicated
`MeetingCanceled` protobuf message and published to the `meet.meeting.canceled`
Kafka topic. No event SHALL be published when a cancellation is rejected.

#### Scenario: Successful cancellation publishes an event atomically

- **WHEN** a meeting is successfully canceled (manually or automatically)
- **THEN** exactly one `MeetingCanceledEvent` is persisted in the same
  transaction as the status change and later relayed to Kafka by the outbox
  relay

#### Scenario: Event carries invitee list at cancellation time

- **WHEN** a meeting is canceled and has active invitees
- **THEN** the published `MeetingCanceledEvent` carries each active invitee's
  account ID, email, display name, RSVP status, and invited-at timestamp

#### Scenario: Rejected cancellation publishes no event

- **WHEN** a cancellation attempt is rejected for authorization, not-found, or
  invalid-status-transition reasons
- **THEN** no `MeetingCanceledEvent` is persisted or published

#### Scenario: Missing proto mapper causes startup failure

- **WHEN** the application starts without a registered `OutboxEventProtoMapper`
  for `MeetingCanceledEvent`
- **THEN** the first attempt to publish that event type throws
  `IllegalStateException` and the event is not relayed, so the mapper MUST be
  present for the service to function correctly
