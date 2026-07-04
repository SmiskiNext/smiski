## ADDED Requirements

### Requirement: Invitee can accept a meeting invitation

The system SHALL allow an authenticated invitee to accept a pending meeting
invitation by sending a PATCH request to
`/api/v1/meetings/{meetingId}/invitees/me` with `{ "response": "ACCEPTED" }`.

#### Scenario: Successful acceptance of pending invitation

- **WHEN** an authenticated user sends PATCH
  `/api/v1/meetings/{meetingId}/invitees/me` with body
  `{ "response": "ACCEPTED" }`
- **AND** a MeetingInvitee record exists with matching meetingId and the user's
  userId
- **AND** the invitee status is PENDING
- **THEN** the system SHALL transition the invitee status to ACCEPTED
- **AND** the system SHALL set respondedAt to the current timestamp
- **AND** the system SHALL publish an InviteeAcceptedEvent to Kafka topic
  `meeting-management.invitee.accepted`
- **AND** the system SHALL return HTTP 200 with the updated invitee data in
  JSend success format

#### Scenario: Invitee not found for user

- **WHEN** an authenticated user sends PATCH
  `/api/v1/meetings/{meetingId}/invitees/me`
- **AND** no MeetingInvitee record exists matching the meetingId and the user's
  userId
- **THEN** the system SHALL return HTTP 404 with JSend fail containing error
  code INVITEE_NOT_FOUND

#### Scenario: Invalid status transition for acceptance

- **WHEN** an authenticated user sends PATCH to accept
- **AND** the invitee status is DECLINED
- **THEN** the system SHALL return HTTP 409 with JSend fail containing error
  code INVALID_INVITEE_TRANSITION

### Requirement: Invitee can decline a meeting invitation

The system SHALL allow an authenticated invitee to decline a pending or accepted
meeting invitation by sending a PATCH request to
`/api/v1/meetings/{meetingId}/invitees/me` with `{ "response": "DECLINED" }`.

#### Scenario: Successful decline of pending invitation

- **WHEN** an authenticated user sends PATCH
  `/api/v1/meetings/{meetingId}/invitees/me` with body
  `{ "response": "DECLINED" }`
- **AND** a MeetingInvitee record exists with matching meetingId and the user's
  userId
- **AND** the invitee status is PENDING
- **THEN** the system SHALL transition the invitee status to DECLINED
- **AND** the system SHALL set respondedAt to the current timestamp
- **AND** the system SHALL publish an InviteeDeclinedEvent to Kafka topic
  `meeting-management.invitee.declined`
- **AND** the system SHALL return HTTP 200 with the updated invitee data in
  JSend success format

#### Scenario: Successful decline of previously accepted invitation

- **WHEN** an authenticated user sends PATCH to decline
- **AND** the invitee status is ACCEPTED
- **THEN** the system SHALL transition the invitee status to DECLINED (ACCEPTED
  -> DECLINED is valid)
- **AND** the system SHALL publish an InviteeDeclinedEvent

#### Scenario: Invalid status transition for decline

- **WHEN** an authenticated user sends PATCH to decline
- **AND** the invitee status is already DECLINED
- **THEN** the system SHALL return HTTP 409 with JSend fail containing error
  code INVALID_INVITEE_TRANSITION

### Requirement: Get pending invitations for authenticated user

The system SHALL provide a GET endpoint at
`/api/v1/users/{userId}/invitations:pending` that returns all PENDING
invitations for the authenticated user with meeting metadata.

#### Scenario: User has pending invitations

- **WHEN** an authenticated user sends GET
  `/api/v1/users/{userId}/invitations:pending`
- **AND** the userId in the path matches the authenticated user's ID
- **AND** the user has MeetingInvitee records with status PENDING
- **THEN** the system SHALL return HTTP 200 with a JSend success containing a
  list of pending invitation objects
- **AND** each object SHALL include: inviteeId, meetingId, meetingTitle,
  meetingShortCode, startTime, hostDisplayName, invitedAt

#### Scenario: User has no pending invitations

- **WHEN** an authenticated user sends GET
  `/api/v1/users/{userId}/invitations:pending`
- **AND** the user has no MeetingInvitee records with status PENDING
- **THEN** the system SHALL return HTTP 200 with a JSend success containing an
  empty list

#### Scenario: User ID mismatch

- **WHEN** an authenticated user sends GET
  `/api/v1/users/{userId}/invitations:pending`
- **AND** the userId in the path does NOT match the authenticated user's ID
- **THEN** the system SHALL return HTTP 403 with JSend fail containing error
  code NOT_OWNER

### Requirement: Respond endpoint request validation

The system SHALL validate the request body of the respond endpoint.

#### Scenario: Missing response field

- **WHEN** an authenticated user sends PATCH
  `/api/v1/meetings/{meetingId}/invitees/me` with empty body or missing
  `response` field
- **THEN** the system SHALL return HTTP 400 with JSend fail containing
  validation errors

#### Scenario: Invalid response value

- **WHEN** an authenticated user sends PATCH with
  `{ "response": "INVALID_VALUE" }`
- **THEN** the system SHALL return HTTP 400 with JSend fail containing
  validation errors
