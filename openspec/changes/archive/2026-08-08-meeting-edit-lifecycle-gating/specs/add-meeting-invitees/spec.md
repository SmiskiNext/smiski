## MODIFIED Requirements

### Requirement: Status-gated invitee creation

The system SHALL allow invitee creation when the meeting status is `SCHEDULED`
or `RUNNING`. The system SHALL reject invitee creation for meetings whose status
is `COMPLETED` or `CANCELED` with an invalid-status Problem Details response,
and SHALL not create any invitee or publish any event.

#### Scenario: Scheduled meeting accepts invitee creation

- **WHEN** the host adds invitees to a `SCHEDULED` meeting
- **THEN** the invitees are created and persisted

#### Scenario: Running meeting accepts invitee creation

- **WHEN** the host adds invitees to a `RUNNING` meeting
- **THEN** the invitees are created and persisted and the meeting remains
  `RUNNING`

#### Scenario: Completed or canceled meeting rejects invitee creation

- **WHEN** the host attempts to add invitees to a `COMPLETED` or `CANCELED`
  meeting
- **THEN** the request is rejected with an invalid-status Problem Details
  response, no invitee is created, and no event is published
