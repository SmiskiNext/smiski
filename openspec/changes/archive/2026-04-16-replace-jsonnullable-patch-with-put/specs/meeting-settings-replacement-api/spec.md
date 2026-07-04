# ADDED Requirements

## Requirement: Meeting settings updates are PUT-only

The system SHALL expose `PUT /api/v1/meetings/{id}/settings` as the only update
endpoint for meeting settings and SHALL NOT expose
`PATCH /api/v1/meetings/{id}/settings`.

### Scenario: Replace meeting settings with PUT

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with a valid full
  settings payload
- **THEN** the system SHALL replace the meeting settings using the supplied
  values and return the updated `MeetingSettingsResponse`

### Scenario: Patch meeting settings endpoint removed

- **WHEN** a client sends `PATCH /api/v1/meetings/{id}/settings`
- **THEN** the system SHALL treat the operation as unavailable because the PATCH
  endpoint no longer exists in the API contract

## Requirement: Meeting settings PUT preserves authorization and domain rules

The system SHALL enforce the same business rules for PUT meeting settings that
the previous update flow enforced for PATCH meeting settings.

### Scenario: Only host can replace meeting settings

- **WHEN** a non-host user sends `PUT /api/v1/meetings/{id}/settings`
- **THEN** the system SHALL reject the request as not authorized

### Scenario: Only scheduled or live meetings can be updated

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` for an ENDED or
  CANCELLED meeting
- **THEN** the system SHALL reject the request with the same invalid-status
  behavior used by the existing meeting settings update flow

### Scenario: Max participants ceiling enforced

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with
  `maxParticipants` above the configured system ceiling
- **THEN** the system SHALL reject the request with an invalid-settings error

### Scenario: Allow-all policy blocks maxParticipants change

- **WHEN** the effective PUT payload uses `admissionPolicy = ALLOW_ALL`
- **THEN** the system SHALL reject any request that attempts to set
  `maxParticipants`

## Requirement: Meeting settings PUT defines explicit nullable field semantics

The system SHALL interpret nullable meeting settings fields in the PUT payload
explicitly rather than through omitted-field patch semantics.

### Scenario: Null timeout clears join request timeout

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with
  `joinRequestTimeoutSeconds = null`
- **THEN** the system SHALL clear the meeting join request timeout

### Scenario: Null password clears meeting password

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with
  `password = null`
- **THEN** the system SHALL clear the stored meeting password hash

### Scenario: Non-null password updates meeting password

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with a non-null
  password value
- **THEN** the system SHALL hash that value before persisting the meeting
  settings

## Requirement: Meeting settings PUT preserves side effects and event integrity

The system SHALL preserve the existing side effects triggered by successful
meeting settings updates.

### Scenario: Meeting settings update event still published

- **WHEN** `PUT /api/v1/meetings/{id}/settings` succeeds
- **THEN** the system SHALL publish `MeetingSettingsUpdatedEvent` with the same
  event type and topic used by the existing meeting settings update flow

### Scenario: Live meeting access opening auto-approves pending requests

- **WHEN** `PUT /api/v1/meetings/{id}/settings` makes a LIVE meeting more
  permissive by changing admission policy to `ALLOW_ALL` or changing
  `allowGuest` from `false` to `true`
- **THEN** the system SHALL auto-approve pending join requests using the same
  approval helper used by the existing meeting settings update flow
