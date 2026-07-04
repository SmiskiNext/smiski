# ADDED Requirements

## Requirement: Host can view the invitee list in MeetingDetailDialog

The `MeetingDetailDialog` for a SCHEDULED meeting SHALL display an Invitees
section that loads and renders all invitees with their statuses when the dialog
is opened by the host.

### Scenario: Invitees section renders with populated list

- **WHEN** a host opens the `MeetingDetailDialog` for a SCHEDULED meeting that
  has invitees
- **THEN** the system SHALL call `getInvitees` with the meeting's `meetingId`
- **AND** each invitee row SHALL display the invitee email (fallback to
  `displayName`, then `inviteeId` when neither field is present)
- **AND** each row SHALL display an invite-status badge with values PENDING
  (yellow), ACCEPTED (green), or DECLINED (red)
- **AND** each row SHALL display a token-status badge with values PENDING, USED,
  REVOKED, or EXPIRED

### Scenario: Invitees section renders empty state

- **WHEN** the host opens the dialog and the meeting has no invitees
- **THEN** the system SHALL render a localized empty-state message in the
  Invitees section

### Scenario: Invitees section renders loading state

- **WHEN** `getInvitees` is in flight
- **THEN** the system SHALL render a loading indicator in place of the invitee
  list

### Scenario: Invitees section renders error state

- **WHEN** `getInvitees` fails
- **THEN** the system SHALL render a localized error message in the Invitees
  section

## Requirement: Host can add an invitee by email

The Invitees section SHALL provide a single-email input and an Add button that
submits to `addInvitee`.

### Scenario: Host adds a valid invitee

- **WHEN** a host enters a valid email address into the add-invitee input and
  activates the Add button
- **AND** the invitee count is below 10
- **THEN** the system SHALL call `addInvitee` with the meeting's `meetingId` and
  the entered email
- **AND** on success the system SHALL refresh the invitee list and clear the
  email input

### Scenario: Host adds an invitee when the cap is reached

- **WHEN** the invitee list already contains 10 items
- **THEN** the system SHALL hide the add-invitee input and Add button

### Scenario: Add invitee fails with a server error

- **WHEN** `addInvitee` returns an `ApiFailError`
- **THEN** the system SHALL display the error message returned by the server
  near the add form
- **AND** the email input value SHALL be preserved for correction

### Scenario: Add invitee fails with an invalid email format

- **WHEN** the host enters a malformed email and activates Add
- **THEN** the system SHALL display an inline validation error without calling
  the API

## Requirement: Host can resend an invite

A Resend button SHALL appear on invitee rows whose token status is PENDING,
EXPIRED, or REVOKED.

### Scenario: Host resends a pending or expired invite

- **WHEN** a host activates the Resend button on an invitee row whose token
  status is PENDING, EXPIRED, or REVOKED
- **THEN** the system SHALL call `resendInvite` with the meeting's `meetingId`
  and the row's `inviteeId`
- **AND** on success the system SHALL refresh the invitee list

### Scenario: Resend fails

- **WHEN** `resendInvite` returns an error
- **THEN** the system SHALL display a localized error message near the affected
  row
- **AND** the rest of the invitee list SHALL remain interactive

### Scenario: Resend button not visible for used tokens

- **WHEN** an invitee's token status is USED
- **THEN** the Resend button SHALL NOT be rendered for that row

## Requirement: Host can revoke an invite

A Revoke button SHALL appear on invitee rows whose invite status is PENDING and
token status is PENDING.

### Scenario: Host revokes a pending invite

- **WHEN** a host activates the Revoke button on an invitee row that meets the
  visibility condition
- **THEN** the system SHALL call `revokeInvite` with the meeting's `meetingId`
  and the row's `inviteeId`
- **AND** on success the system SHALL refresh the invitee list

### Scenario: Revoke fails

- **WHEN** `revokeInvite` returns an error
- **THEN** the system SHALL display a localized error message near the affected
  row
- **AND** the rest of the invitee list SHALL remain interactive

### Scenario: Revoke button not visible for non-pending invitees

- **WHEN** an invitee's token status is not PENDING or the invite status is not
  PENDING
- **THEN** the Revoke button SHALL NOT be rendered for that row
