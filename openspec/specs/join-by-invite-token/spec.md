# Purpose

Define the backend API endpoint that allows a client holding an invite token to
validate it and proceed to the meeting join flow without entering a password.
Establishes the token-based join contract between the invite link, the backend
validation endpoint, and the existing join infrastructure.

# ADDED Requirements

## Requirement: Token validation endpoint

The system SHALL provide a `POST /api/v1/meetings/invite-tokens/validate`
endpoint that accepts an invite token string and returns validation result and
meeting metadata.

### Scenario: Valid token returns meeting access info

- **WHEN** a client submits a POST to `/api/v1/meetings/invite-tokens/validate`
  with body `{ "token": "validInviteTokenString" }`
- **AND** the token is valid (HMAC verified, not expired, not revoked)
- **THEN** the system SHALL return HTTP 200 with body
  `{ "valid": true, "meetingId": "...", "shortCode": "ABC12345", "preApproved": boolean, "requiresJoinRequest": boolean }`
- **AND** `preApproved` SHALL be `true` when the token's invitee is a resolved
  registered user AND the meeting's `admissionPolicy` is `ALLOW_ALL`
- **AND** `requiresJoinRequest` SHALL be `true` when the meeting's
  `admissionPolicy` is `MANUAL_APPROVAL` regardless of pre-approval

### Scenario: Invalid token returns error details

- **WHEN** a client submits a token that fails HMAC verification or is expired
- **THEN** the system SHALL return HTTP 200 with body
  `{ "valid": false, "error": "INVITE_TOKEN_EXPIRED" | "INVITE_TOKEN_INVALID" | "INVITE_TOKEN_REVOKED" | "INVITE_TOKEN_USED" | "INVITE_TOKEN_NOT_FOUND" }`
- **AND** the response SHALL NOT include the meeting short code or any meeting
  metadata

### Scenario: Token validation requires unauthenticated user

- **WHEN** an authenticated user calls the validate endpoint
- **THEN** the system SHALL still accept the request (the endpoint is public so
  invite link holders need not be logged in)
- **AND** the authenticated user identity SHALL NOT be used in token validation

### Scenario: Token references non-existent meeting

- **WHEN** a valid-format token is parsed but the referenced meeting ID does not
  exist
- **THEN** the system SHALL return HTTP 200 with
  `{ "valid": false, "error": "MEETING_NOT_FOUND" }`

### Scenario: Token references cancelled or ended meeting

- **WHEN** the token is structurally valid but the referenced meeting has status
  `CANCELLED` or `ENDED`
- **THEN** the system SHALL return HTTP 200 with
  `{ "valid": false, "error": "MEETING_UNAVAILABLE" }`

## Requirement: Token validation marks token as USED

When a token is successfully validated, the system SHALL record that the token
has been used once. A `USED` token is a terminal state and subsequent validation
attempts SHALL be rejected.

### Scenario: First validation marks token as USED

- **WHEN** a valid token is validated for the first time
- **THEN** the system SHALL update the `InviteToken` record status to `USED`

### Scenario: Subsequent validation of a USED token is rejected

- **WHEN** a token with status `USED` is validated again
- **THEN** the system SHALL return `valid: false` with error code
  `INVITE_TOKEN_USED`
- **AND** the validation SHALL NOT return `valid: true`

## Requirement: Client proceeds to join using validated token result

After a successful token validation, the client uses the returned `shortCode` to
join the meeting via the existing join flow.

### Scenario: Client joins meeting after token validation

- **WHEN** a client has successfully validated an invite token and received
  `{ "valid": true, "shortCode": "ABC12345" }`
- **AND** `requiresJoinRequest` is `false` (admissionPolicy is ALLOW_ALL)
- **THEN** the client SHALL proceed with the standard join flow by calling
  `POST /api/v1/meetings/{meetingId}/join` with the user's identity (or guest
  deviceId if not authenticated)
- **AND** the client SHALL NOT prompt the user to enter a meeting password

### Scenario: Client enters waiting room after token validation with manual approval

- **WHEN** a client has successfully validated an invite token and received
  `{ "valid": true, "requiresJoinRequest": true }`
- **THEN** the client SHALL proceed with the standard join flow, which will
  create a PENDING join request
- **AND** the host SHALL be notified of the pending request through the existing
  SSE mechanism

## Requirement: Invite token join link format

The invite link format SHALL use a dedicated route that initiates the token
validation flow.

### Scenario: Invite link format

- **WHEN** the notification service builds an invite link for an invitee
- **THEN** the URL SHALL be structured as
  `{frontendBaseUrl}/join?token={inviteTokenString}`
- **AND** the URL SHALL NOT include `shortCode` or `password` as query
  parameters
- **AND** `frontendBaseUrl` SHALL be configured via
  `notification.properties.invitation.joinBaseUrl`

### Scenario: Frontend extracts token from URL and calls validate endpoint

- **WHEN** a user opens an invite link in the Android or Web app
- **THEN** the client SHALL extract the `token` query parameter
- **AND** the client SHALL call `POST /api/v1/meetings/invite-tokens/validate`
  with the token
- **AND** on `valid: true`, the client SHALL proceed with the join flow using
  the returned `shortCode`
- **AND** on `valid: false`, the client SHALL show an appropriate error screen
  (expired, revoked, meeting not found)
