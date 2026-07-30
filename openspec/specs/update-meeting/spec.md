# update-meeting Specification

## Purpose

TBD - created by archiving change add-update-meeting. Update Purpose after
archive.

## Requirements

### Requirement: Host-only meeting update endpoint

The system SHALL expose `PUT /api/1/meetings/{id}` for updating a meeting. The
acting account SHALL be resolved from the configured account header, the tenant
SHALL be resolved from the tenant context, and only the meeting host SHALL be
authorized to update the meeting. A successful update SHALL return `200 OK` with
the complete meeting snapshot excluding the tenant identifier.

#### Scenario: Host updates a meeting

- **WHEN** the host sends a valid `PUT /api/1/meetings/{id}` request with the
  account header
- **THEN** the system persists the permitted changes and returns `200 OK` with
  the full meeting snapshot

#### Scenario: Non-host update is rejected

- **WHEN** an account that is not the meeting host sends an update request
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and does not change the meeting

#### Scenario: Missing account identity is rejected

- **WHEN** the update request does not contain the configured account header
- **THEN** the system returns `400` Problem Details and does not change the
  meeting

#### Scenario: Unknown meeting is rejected

- **WHEN** the update request references an ID that does not exist in the tenant
- **THEN** the system returns a meeting-not-found Problem Details response and
  does not publish an event

### Requirement: Status-aware mutable fields

The system SHALL allow the host to update `title`, `description`, and
`issueLink` when the meeting status is `SCHEDULED` or `RUNNING`. The system
SHALL allow `zoneId` and `timeRange` updates only when the meeting status is
`SCHEDULED`. The system SHALL reject every update to a meeting with status
`COMPLETED` or `CANCELED`. Settings are no longer part of this endpoint; they
are replaced through `PUT /api/1/meetings/{id}/settings` (see the
`update-meeting-settings` capability).

#### Scenario: Scheduled meeting accepts all mutable information fields

- **WHEN** the host updates any supported combination of title, description,
  issue link, zone ID, and time range on a `SCHEDULED` meeting
- **THEN** all supplied valid changes are persisted atomically

#### Scenario: Running meeting accepts information

- **WHEN** the host updates title, description, or issue link on a `RUNNING`
  meeting
- **THEN** those changes are persisted and the meeting remains `RUNNING`

#### Scenario: Running meeting rejects scheduled fields

- **WHEN** the host attempts to change `zoneId` or `timeRange` on a `RUNNING`
  meeting
- **THEN** the update is rejected, no field from that request is persisted, and
  no update event is published

#### Scenario: Completed meeting rejects updates

- **WHEN** the host attempts to update any field on a `COMPLETED` meeting
- **THEN** the update is rejected and the meeting remains unchanged

#### Scenario: Canceled meeting rejects updates

- **WHEN** the host attempts to update any field on a `CANCELED` meeting
- **THEN** the update is rejected and the meeting remains unchanged

### Requirement: Input validation for updated values

The system SHALL validate update values using the same constraints as meeting
creation for the information fields it accepts: non-blank title and description,
a complete issue link, a valid IANA `zoneId`, and a time range whose start
precedes its end and satisfies the scheduled start-time rule. Invalid requests
SHALL return `400` Problem Details with `VALIDATION_ERROR` and SHALL NOT change
the meeting. The endpoint SHALL NOT accept a settings object.

#### Scenario: Invalid zone ID is rejected

- **WHEN** a scheduled update contains a non-IANA `zoneId`
- **THEN** the system returns `400` validation Problem Details and leaves the
  meeting unchanged

#### Scenario: Invalid time range is rejected

- **WHEN** a scheduled update contains a time range whose start is not before
  its end or is outside the allowed start-time window
- **THEN** the system returns `400` validation Problem Details and leaves the
  meeting unchanged

#### Scenario: Blank information field is rejected

- **WHEN** the request contains a blank title or description
- **THEN** the system returns `400` validation Problem Details and persists no
  part of the update

### Requirement: Change-sensitive update events

The system SHALL publish the meeting info-update event on the
`meet.meeting.info.updated` topic with the CloudEvent type
`io.github.smiskinext.meet.meeting.info.updated.v1` when at least one of
`title`, `description`, `issueLink`, `zoneId`, or `timeRange` changes. The event
SHALL include the meeting identity, host, acting account, status, old snapshot,
new snapshot, the meeting's current invitee list (each carrying account id,
email, display name, and participation status), and the update timestamp. A
successful update with no effective value changes SHALL publish no event. This
endpoint SHALL NOT publish `meeting.settings.update`; settings events are
produced only by `PUT /api/1/meetings/{id}/settings`.

#### Scenario: Information change publishes information event

- **WHEN** an authorized update changes one or more information fields
- **THEN** exactly one info-update event is persisted atomically with the
  meeting change on the `meet.meeting.info.updated` topic

#### Scenario: Info-update event carries the current invitee list

- **WHEN** an authorized update changes one or more information fields on a
  meeting that has invitees
- **THEN** the published info-update event carries an entry for each current
  invitee with that invitee's email, display name, account id, and participation
  status

#### Scenario: No-op update publishes no event

- **WHEN** an authorized update contains values equal to the persisted values
- **THEN** the system returns the unchanged meeting snapshot and persists no
  update event

#### Scenario: Failed update publishes no event

- **WHEN** authorization, status, or validation checks reject an update
- **THEN** no meeting update and no update event is persisted

#### Scenario: Information update does not publish a settings event

- **WHEN** an authorized information update succeeds
- **THEN** no `meeting.settings.update` event is published by this endpoint
