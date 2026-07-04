# MODIFIED Requirements

## Requirement: Android room join request integration

The Android app SHALL resolve meeting entry requirements before attempting a
LiveKit connection and SHALL include meeting passwords in join requests when the
target meeting is protected.

### Scenario: Unprotected meeting joins without password prompt

- **WHEN** the user submits a valid meeting short code in `PreJoinFragment`
- **THEN** the system SHALL call `GET /api/v1/meetings:byShortCode?code={code}`
- **THEN** if `settings.requirePassword` is `false`, the system SHALL continue
  to `POST /api/v1.0/meetings/{id}:requestJoin` without requiring password entry

### Scenario: Protected meeting requires password before join submission

- **WHEN** the short-code lookup succeeds and `settings.requirePassword` is
  `true`
- **THEN** the Android client SHALL store the resolved meeting UUID for the
  current join attempt
- **THEN** the pre-join flow SHALL require the user to enter a password before
  sending `POST /api/v1.0/meetings/{id}:requestJoin`

### Scenario: Protected meeting join submits password to backend

- **WHEN** the user submits a join request for a protected meeting after
  entering a password
- **THEN** the Android client SHALL include `displayName`, `deviceId`, and
  `password` in `MeetingManagementJoinRequestRequest`
- **THEN** approved and pending join handling SHALL continue using the existing
  backend response contract

### Scenario: Meeting lookup failure blocks join submission

- **WHEN** `GET /api/v1/meetings:byShortCode?code={code}` returns not found or
  fails before join submission
- **THEN** the Android client SHALL NOT call the join request endpoint
- **THEN** the pre-join flow SHALL keep the user on the current screen and show
  meeting-code or retry feedback appropriate to the failure type
