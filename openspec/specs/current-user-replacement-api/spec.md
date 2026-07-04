# Purpose

TBD: Define the main specification for current-user replacement APIs.

# ADDED Requirements

## Requirement: Current user profile updates are PUT-only

The system SHALL expose `PUT /api/v1/me` as the only profile update endpoint for
the authenticated user and SHALL NOT expose `PATCH /api/v1/me`.

### Scenario: Replace profile with PUT

- **WHEN** an authenticated client sends `PUT /api/v1/me` with valid `fullName`,
  `username`, and `avatarUrl`
- **THEN** the system SHALL replace the stored profile fields using the request
  body and return the updated `UserResponse`

### Scenario: Clear avatar with PUT

- **WHEN** an authenticated client sends `PUT /api/v1/me` with
  `avatarUrl = null`
- **THEN** the system SHALL clear the stored avatar URL and return the updated
  `UserResponse`

### Scenario: Preserve user profile update event flow

- **WHEN** `PUT /api/v1/me` successfully updates the user profile
- **THEN** the system SHALL continue publishing `UserUpdatedEvent` with the same
  event type and topic used by the existing full-profile update flow

### Scenario: PATCH profile endpoint removed

- **WHEN** a client sends `PATCH /api/v1/me`
- **THEN** the system SHALL treat the operation as unavailable because the PATCH
  endpoint no longer exists in the API contract

## Requirement: Current user preferences updates replace the full preferences document

The system SHALL expose `PUT /api/v1/me/preferences` for authenticated users and
SHALL replace the stored preferences document using the raw JSON object supplied
in the request body.

### Scenario: Replace preferences with raw JSON object

- **WHEN** an authenticated client sends `PUT /api/v1/me/preferences` with a
  JSON object containing arbitrary keys
- **THEN** the system SHALL store exactly those keys as the current preferences
  document and return them in `UserPreferencesResponse`

### Scenario: Empty object clears preferences

- **WHEN** an authenticated client sends `PUT /api/v1/me/preferences` with `{}`
- **THEN** the system SHALL clear all stored preferences and return an empty
  `settings` object in `UserPreferencesResponse`

### Scenario: Null request body is rejected

- **WHEN** an authenticated client sends `PUT /api/v1/me/preferences` with a
  null request body
- **THEN** the system SHALL reject the request at the presentation boundary as
  invalid input

### Scenario: PATCH preferences endpoint removed

- **WHEN** a client sends `PATCH /api/v1/me/preferences`
- **THEN** the system SHALL treat the operation as unavailable because the PATCH
  endpoint no longer exists in the API contract
