# Purpose

Define the generation, storage, validation, and revocation of per-invitee
HMAC-SHA256 invite tokens used in place of short-code + password sharing for
meeting invitations.

# ADDED Requirements

## Requirement: Invite token generation on meeting schedule

When a meeting is scheduled with one or more invitees, the system SHALL generate
a cryptographically signed invite token for each invitee and persist it
alongside the `MeetingInvitee` record.

### Scenario: Invite token created for each invitee on schedule

- **WHEN** a host successfully schedules a meeting with one or more invitee
  emails
- **THEN** the system SHALL create one `InviteToken` record per invitee
- **AND** the token SHALL have status `PENDING`
- **AND** the token SHALL have `expiresAt` set to 7 days after the current time
- **AND** the token string SHALL be included in the
  `MeetingInvitationsSentEvent` for each invitee

### Scenario: Invite token generation is idempotent per invitee

- **WHEN** the same invitee email appears multiple times in the invitee list for
  a single meeting
- **THEN** the system SHALL only create one `InviteToken` for that invitee
- **AND** the `MeetingInvitationsSentEvent` SHALL only be sent once for that
  email address

### Scenario: No invite token created when meeting has no invitees

- **WHEN** a host schedules a meeting with an empty invitee list
- **THEN** the system SHALL NOT create any `InviteToken` records
- **AND** the `MeetingInvitationsSentEvent` SHALL NOT be published

## Requirement: Invite token structure

An invite token is a URL-safe signed string constructed from a base64-encoded
HMAC signature concatenated with delimited metadata.

### Scenario: Token format contains signature, meetingId, inviteeId, and expiry

- **WHEN** a token is generated
- **THEN** its string value SHALL be constructed as
  `base64(HMAC-SHA256(meetingId | inviteeId | expiryEpoch, serverSecret)) | meetingId | inviteeId | expiryEpoch`
- **AND** the `|` character SHALL NOT appear in any component value
  (pipe-delimited, not base64 containing pipes)
- **AND** the token SHALL NOT contain the invitee's raw password

### Scenario: Token hash stored in database, not the raw token

- **WHEN** an `InviteToken` is persisted
- **THEN** the database SHALL store `SHA-256(rawToken)` in the `token_hash`
  column
- **AND** the raw token string SHALL NOT be stored in the database
- **AND** the raw token SHALL only ever be transmitted to the invitee via email

## Requirement: Invite token validation

The system SHALL provide a token validation operation that verifies authenticity
and expiry without a database lookup in the happy path.

### Scenario: Valid token passes validation

- **WHEN** a client submits a token string to the validate endpoint
- **THEN** the system SHALL recompute the HMAC-SHA256 using the embedded
  meetingId, inviteeId, expiry, and the server secret
- **AND** if the recomputed HMAC matches the provided signature AND the current
  time is before `expiryEpoch`
- **AND** the associated `InviteToken` record in the database has status
  `PENDING`
- **THEN** the system SHALL return `valid: true` with the meeting short code and
  pre-approval status

### Scenario: Expired token fails validation

- **WHEN** a client submits a token whose `expiryEpoch` is in the past (more
  than 60 seconds ago)
- **THEN** the system SHALL return `valid: false` with error code
  `INVITE_TOKEN_EXPIRED`

### Scenario: Tampered token fails validation

- **WHEN** a client submits a token whose HMAC signature does not match the
  recomputed value
- **THEN** the system SHALL return `valid: false` with error code
  `INVITE_TOKEN_INVALID`

### Scenario: Revoked token fails validation

- **WHEN** a client submits a token whose HMAC and expiry are valid BUT the
  associated `InviteToken` record has status `REVOKED`
- **THEN** the system SHALL return `valid: false` with error code
  `INVITE_TOKEN_REVOKED`

### Scenario: Token not found in database fails validation

- **WHEN** a client submits a token whose HMAC and expiry are valid BUT no
  matching `InviteToken` record exists in the database
- **THEN** the system SHALL return `valid: false` with error code
  `INVITE_TOKEN_NOT_FOUND`

### Scenario: Used token fails validation

- **WHEN** a client submits a token whose `InviteToken` record has status `USED`
- **THEN** the system SHALL return `valid: false` with error code
  `INVITE_TOKEN_USED`
- **AND** the `USED` status SHALL be treated as a terminal rejection (the token
  cannot be reused after it has been consumed)

## Requirement: Invite token expiry and extension

Invite tokens have a maximum lifetime. The host can regenerate a new token for
an invitee, which revokes the old one and extends validity.

### Scenario: Token expiry is 7 days from creation

- **WHEN** an `InviteToken` is created during meeting scheduling
- **THEN** the `expiresAt` timestamp SHALL be set to the current time plus 7
  days
- **AND** the token SHALL NOT be usable after `expiresAt` (subject to 60-second
  clock-skew tolerance)

### Scenario: Resending invite creates new token and revokes old

- **WHEN** a host requests to resend an invite for a specific invitee
- **THEN** the system SHALL mark the existing `InviteToken` record as `REVOKED`
- **AND** the system SHALL create a new `InviteToken` with a fresh token string
  and a new `expiresAt` set to now plus 7 days
- **AND** the new token SHALL be included in the re-sent invitation notification

## Requirement: Invite token revocation

The host can revoke an individual invitee's token, preventing future use.

### Scenario: Host revokes invitee token

- **WHEN** the host calls the revoke endpoint for an invitee on their meeting
- **THEN** the system SHALL mark the `InviteToken` record status as `REVOKED`
- **AND** subsequent validation attempts for that token SHALL return
  `INVITE_TOKEN_REVOKED`

### Scenario: Revocation requires host authorization

- **WHEN** a user who is not the meeting host attempts to revoke an invite token
- **THEN** the system SHALL return HTTP 403 Forbidden

### Scenario: Revocation of already-revoked or expired token is idempotent

- **WHEN** the host requests revocation of a token that is already `REVOKED` or
  `EXPIRED`
- **THEN** the system SHALL return HTTP 204 No Content
- **AND** SHALL NOT return an error
