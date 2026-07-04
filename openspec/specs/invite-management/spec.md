# Purpose

Define the host-facing REST API for managing invitees on a scheduled meeting:
listing invitees with their token status, resending invite tokens, and revoking
individual invites.

# ADDED Requirements

## Requirement: List invitees for a meeting

Hosts can retrieve the list of invitees for their meeting, including token
status.

### Scenario: Host lists invitees for their scheduled meeting

- **WHEN** an authenticated host calls
  `GET /api/v1/meetings/{meetingId}/invitees`
- **AND** the meeting exists and the authenticated user is the host
- **THEN** the system SHALL return HTTP 200 with a list of invitee records
- **AND** each record SHALL include: `inviteeId`, `email`, `displayName`,
  `status` (PENDING | ACCEPTED | DECLINED), `inviteTokenStatus` (PENDING | USED
  | REVOKED | EXPIRED), `invitedAt`, `respondedAt`

### Scenario: Non-host cannot list invitees

- **WHEN** an authenticated user calls
  `GET /api/v1/meetings/{meetingId}/invitees`
- **AND** the user is not the meeting host
- **THEN** the system SHALL return HTTP 403 Forbidden

### Scenario: Host lists invitees for meeting they do not own

- **WHEN** a host calls `GET /api/v1/meetings/{meetingId}/invitees`
- **AND** the meeting exists but the host does not own it
- **THEN** the system SHALL return HTTP 403 Forbidden

### Scenario: Meeting not found

- **WHEN** a user calls `GET /api/v1/meetings/{meetingId}/invitees`
- **AND** no meeting with that ID exists
- **THEN** the system SHALL return HTTP 404 Not Found

### Scenario: No invitees exist for the meeting

- **WHEN** a host calls `GET /api/v1/meetings/{meetingId}/invitees`
- **AND** the meeting has no invitees
- **THEN** the system SHALL return HTTP 200 with an empty list

## Requirement: Resend invite to a specific invitee

A host can resend an invite to a specific invitee, which revokes the old token
and creates a new one with a fresh expiry.

### Scenario: Host resends invite to a pending invitee

- **WHEN** a host calls
  `POST /api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend`
- **AND** the meeting is in SCHEDULED status
- **AND** the invitee record exists for that meeting
- **THEN** the system SHALL mark the existing `InviteToken` as `REVOKED`
- **AND** the system SHALL create a new `InviteToken` with a fresh token string
  and expiry of now + 7 days
- **AND** the system SHALL publish a `MeetingInvitationsSentEvent` for this
  invitee with the new token

### Scenario: Host resends invite to an accepted invitee

- **WHEN** a host calls
  `POST /api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend`
- **AND** the invitee status is `ACCEPTED`
- **THEN** the system SHALL revoke the existing token and create a new one (the
  invitee may want a fresh invite for a rescheduled meeting)

### Scenario: Resend fails for cancelled or ended meeting

- **WHEN** a host calls
  `POST /api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend`
- **AND** the meeting status is `CANCELLED` or `ENDED`
- **THEN** the system SHALL return HTTP 409 Conflict with error
  `MEETING_NOT_SCHEDULED`

### Scenario: Resend fails for non-existent invitee

- **WHEN** a host calls
  `POST /api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend`
- **AND** no invitee with that ID exists for the meeting
- **THEN** the system SHALL return HTTP 404 Not Found

## Requirement: Revoke an individual invite

A host can revoke an invite for a specific invitee, which marks the token as
revoked and prevents the invitee from using the invite link.

### Scenario: Host revokes an invite

- **WHEN** a host calls
  `DELETE /api/v1/meetings/{meetingId}/invitees/{inviteeId}`
- **AND** the invitee record exists and belongs to the host's meeting
- **THEN** the system SHALL mark the `InviteToken` as `REVOKED`
- **AND** the `MeetingInvitee` status SHALL remain unchanged (it is a historical
  record)
- **AND** the system SHALL return HTTP 204 No Content

### Scenario: Revoked invitee cannot validate their token

- **WHEN** an invitee whose invite was revoked attempts to validate their invite
  token
- **THEN** the validation endpoint SHALL return
  `{ "valid": false, "error": "INVITE_TOKEN_REVOKED" }`

### Scenario: Revoke fails for non-host

- **WHEN** a user who is not the meeting host calls
  `DELETE /api/v1/meetings/{meetingId}/invitees/{inviteeId}`
- **THEN** the system SHALL return HTTP 403 Forbidden

## Requirement: Add invitee to already-scheduled meeting

A host can add new invitees to an already-scheduled meeting.

### Scenario: Host adds invitee to scheduled meeting

- **WHEN** a host calls `POST /api/v1/meetings/{meetingId}/invitees`
- **AND** the request body contains `{ "email": "invitee@example.com" }`
- **AND** the meeting status is `SCHEDULED`
- **AND** the email is not already in the invitee list for this meeting
- **THEN** the system SHALL create a new `MeetingInvitee` record with status
  `PENDING`
- **AND** the system SHALL create a new `InviteToken` for the invitee
- **AND** the system SHALL publish a `MeetingInvitationsSentEvent` for the new
  invitee

### Scenario: Adding already-existing invitee returns conflict

- **WHEN** a host calls `POST /api/v1/meetings/{meetingId}/invitees`
- **AND** the invitee email is already in the invitee list for that meeting
- **THEN** the system SHALL return HTTP 409 Conflict with error
  `INVITEE_ALREADY_EXISTS`

### Scenario: Adding invitee to non-scheduled meeting returns conflict

- **WHEN** a host calls `POST /api/v1/meetings/{meetingId}/invitees`
- **AND** the meeting status is `LIVE`, `ENDED`, or `CANCELLED`
- **THEN** the system SHALL return HTTP 409 Conflict with error
  `MEETING_NOT_SCHEDULED`

### Scenario: Adding invitee to meeting that does not exist

- **WHEN** a host calls `POST /api/v1/meetings/{meetingId}/invitees`
- **AND** no meeting with that ID exists
- **THEN** the system SHALL return HTTP 404 Not Found
