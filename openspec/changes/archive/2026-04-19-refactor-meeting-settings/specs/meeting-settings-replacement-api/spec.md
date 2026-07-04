# MODIFIED Requirements

## Requirement: Meeting settings updates are PUT-only

The system SHALL expose `PUT /api/v1/meetings/{id}/settings` as the only update
endpoint for meeting settings and SHALL NOT expose
`PATCH /api/v1/meetings/{id}/settings`.

### Scenario: Replace meeting settings with PUT

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with a valid full
  settings payload using the simplified meeting settings schema
- **THEN** the system SHALL replace the meeting settings using the supplied
  values and return the updated `MeetingSettingsResponse`

### Scenario: Patch meeting settings endpoint removed

- **WHEN** a client sends `PATCH /api/v1/meetings/{id}/settings`
- **THEN** the system SHALL treat the operation as unavailable because the PATCH
  endpoint no longer exists in the API contract

## Requirement: Meeting settings PUT preserves authorization and domain rules

The system SHALL enforce the same business rules for PUT meeting settings that
the previous update flow enforced for PATCH meeting settings, except where the
meeting settings contract has been intentionally simplified.

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

## Requirement: Meeting settings PUT uses the simplified settings contract

The system SHALL accept and persist the simplified `MeetingSettings` structure
with only these configurable fields: `admissionPolicy`, `allowGuest`,
`maxParticipants`, `allowScreenShare`, `chatEnabled`, `allowMicrophone`,
`allowVideo`, and nullable `password`.

### Scenario: Removed settings fields are no longer accepted

- **WHEN** a client builds a meeting settings PUT request
- **THEN** the request contract SHALL NOT include `joinRequestTimeoutSeconds`,
  `muteOnEntry`, `recordingEnabled`, or `screenShareMode`

### Scenario: Participant screen sharing is controlled by boolean flag

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with
  `allowScreenShare = false`
- **THEN** the system SHALL disable participant screen sharing while preserving
  the host's implicit ability to share the screen

### Scenario: Participant media permissions are controlled explicitly

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with
  `allowMicrophone` and `allowVideo` values
- **THEN** the system SHALL persist those values as the participant media
  permission policy for the meeting

### Scenario: Default settings use the simplified baseline

- **WHEN** the system creates default meeting settings without caller overrides
- **THEN** the defaults SHALL be `allowScreenShare=true`,
  `allowMicrophone=true`, `allowVideo=true`, `chatEnabled=true`,
  `maxParticipants=100`, and `allowGuest=true`

## Requirement: Meeting settings PUT defines explicit password semantics

The system SHALL interpret the nullable `password` field in the PUT payload
explicitly and SHALL continue storing password values as hashes internally.

### Scenario: Null password clears meeting password

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with
  `password = null`
- **THEN** the system SHALL clear the stored meeting password

### Scenario: Non-null password updates meeting password

- **WHEN** the host sends `PUT /api/v1/meetings/{id}/settings` with a non-null
  password value
- **THEN** the system SHALL hash that value before persisting the meeting
  settings

### Scenario: Response exposes password requirement without revealing password

- **WHEN** the system returns `MeetingSettingsResponse`
- **THEN** the response SHALL expose `requirePassword`
- **THEN** the response SHALL NOT expose the stored password or hash value

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
