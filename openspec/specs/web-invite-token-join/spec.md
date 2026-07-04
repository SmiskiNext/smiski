# ADDED Requirements

## Requirement: Guest join page passes invite token to the join container

The guest join page at `/{locale}/join/{code}` SHALL extract the `token` search
parameter and pass it as an optional prop to `JoinMeetingContainer`.

### Scenario: Guest opens a link with a token search parameter

- **WHEN** a guest navigates to `/{locale}/join/{code}?token=RAW_TOKEN`
- **THEN** the page component SHALL read `RAW_TOKEN` from the `token` search
  param
- **AND** SHALL pass it as `inviteToken` to `JoinMeetingContainer`

### Scenario: Guest opens a link without a token

- **WHEN** a guest navigates to `/{locale}/join/{code}` without a `?token=`
  param
- **THEN** `inviteToken` SHALL be `undefined` and the join flow SHALL proceed as
  before without any token validation step

## Requirement: Join container validates the invite token on mount

When `JoinMeetingContainer` receives a non-empty `inviteToken`, the system SHALL
call `validateToken` before the user can interact with the join form.

### Scenario: Token validation succeeds

- **WHEN** `JoinMeetingContainer` mounts with a valid `inviteToken`
- **THEN** the system SHALL call `validateToken` with the raw token string
- **AND** on success SHALL extract `shortCode` from the response and prefill the
  meeting code field
- **AND** the token validation loading state SHALL be shown while the call is in
  flight

### Scenario: Token is pre-approved and meeting requires no password

- **WHEN** `validateToken` returns `preApproved: true` and the resolved meeting
  has `requirePassword: false`
- **THEN** the system SHALL auto-submit the join request without user
  interaction
- **AND** the user SHALL be taken directly to the meeting room or waiting room
  according to the server's join-request response

### Scenario: Token is pre-approved but meeting requires a password

- **WHEN** `validateToken` returns `preApproved: true` and the resolved meeting
  has `requirePassword: true`
- **THEN** the system SHALL prefill the meeting code field and display the
  password input
- **AND** the system SHALL NOT auto-submit until the user enters the password

### Scenario: Token is not pre-approved

- **WHEN** `validateToken` returns `preApproved: false`
- **THEN** the system SHALL prefill the meeting code field and proceed with the
  normal join flow (waiting room or meeting entry per meeting policy)
- **AND** no automatic submission SHALL occur

### Scenario: Token is invalid or expired

- **WHEN** `validateToken` returns `valid: false` (any error variant)
- **THEN** the system SHALL display a localized error message that identifies
  the failure reason (expired, revoked, already used, or invalid)
- **AND** the meeting code field SHALL remain editable for manual entry
- **AND** the system SHALL NOT block the user from manually joining without a
  token

### Scenario: Token validation network failure

- **WHEN** `validateToken` throws an `ApiError` or network error
- **THEN** the system SHALL display a generic localized error message
- **AND** shall allow the user to proceed with manual join

## Requirement: Meeting code field is locked during token validation

While token validation is in progress the form SHALL prevent user edits.

### Scenario: Form inputs are disabled during validation

- **WHEN** `validateToken` is in flight
- **THEN** the meeting code field and submit button SHALL be disabled
- **AND** a loading indicator SHALL be visible in the Invitees or code field
  area

### Scenario: User manually edits meeting code after token failure

- **WHEN** token validation fails and the user modifies the meeting code field
- **THEN** the token error message SHALL be cleared
- **AND** the flow SHALL proceed as a manual join without any token state
