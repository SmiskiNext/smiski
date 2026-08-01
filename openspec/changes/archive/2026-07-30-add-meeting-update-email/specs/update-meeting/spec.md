## MODIFIED Requirements

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
