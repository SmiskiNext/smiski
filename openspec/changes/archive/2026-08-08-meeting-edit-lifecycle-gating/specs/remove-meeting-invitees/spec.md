## MODIFIED Requirements

### Requirement: Status-gated invitee removal

The system SHALL allow invitee removal when the meeting status is `SCHEDULED` or
`RUNNING`. The system SHALL reject invitee removal for meetings whose status is
`COMPLETED` or `CANCELED` with an invalid-status Problem Details response, and
SHALL not remove any invitee or publish any event.

#### Scenario: Scheduled meeting accepts invitee removal

- **WHEN** the host removes invitees from a `SCHEDULED` meeting
- **THEN** the invitees are soft-deleted and the change is persisted

#### Scenario: Running meeting accepts invitee removal

- **WHEN** the host removes invitees from a `RUNNING` meeting
- **THEN** the invitees are soft-deleted, the change is persisted, and the
  meeting remains `RUNNING`

#### Scenario: Completed or canceled meeting rejects invitee removal

- **WHEN** the host attempts to remove invitees from a `COMPLETED` or `CANCELED`
  meeting
- **THEN** the request is rejected with an invalid-status Problem Details
  response, no invitee is removed, and no event is published
