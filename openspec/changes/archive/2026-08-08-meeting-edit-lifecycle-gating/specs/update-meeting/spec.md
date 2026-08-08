## MODIFIED Requirements

### Requirement: Status-aware mutable fields

The system SHALL allow the host to update `title`, `description`, and
`issueLink` in any meeting status (`SCHEDULED`, `RUNNING`, `COMPLETED`, or
`CANCELED`). The system SHALL allow `zoneId` and `timeRange` updates only when
the meeting status is `SCHEDULED`, and SHALL reject any attempt to change
`zoneId` or `timeRange` when the status is `RUNNING`, `COMPLETED`, or
`CANCELED`. Settings are not part of this endpoint; they are replaced through
`PUT /api/1/meetings/{id}/settings` (see the `update-meeting-settings`
capability).

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

#### Scenario: Completed meeting accepts information fields

- **WHEN** the host updates title, description, or issue link on a `COMPLETED`
  meeting
- **THEN** those changes are persisted and the meeting remains `COMPLETED`

#### Scenario: Completed meeting rejects scheduled fields

- **WHEN** the host attempts to change `zoneId` or `timeRange` on a `COMPLETED`
  meeting
- **THEN** the update is rejected, no field from that request is persisted, and
  no update event is published

#### Scenario: Canceled meeting accepts information fields

- **WHEN** the host updates title, description, or issue link on a `CANCELED`
  meeting
- **THEN** those changes are persisted and the meeting remains `CANCELED`

#### Scenario: Canceled meeting rejects scheduled fields

- **WHEN** the host attempts to change `zoneId` or `timeRange` on a `CANCELED`
  meeting
- **THEN** the update is rejected, no field from that request is persisted, and
  no update event is published
