# Purpose

Define the behavior when a host changes the meeting password on a scheduled
(not-yet-started) meeting: which existing invite tokens are invalidated, what
notifications are sent, and how the host can manage the transition.

# ADDED Requirements

## Requirement: Password change on scheduled meeting invalidates pending invite tokens

When the host updates a scheduled meeting's password, all PENDING invite tokens
SHALL be automatically revoked and a Kafka event SHALL be published so affected
invitees can be notified.

### Scenario: Host changes password on scheduled meeting, pending tokens are invalidated

- **WHEN** a host calls `PUT /api/v1/meetings/{meetingId}/settings` with a new
  `password` value
- **AND** the meeting status is `SCHEDULED`
- **AND** the new password differs from the existing password
- **THEN** the system SHALL mark all `InviteToken` records for that meeting with
  status `PENDING` as `REVOKED`
- **AND** the system SHALL publish a `MeetingInviteTokensInvalidatedEvent` to
  Kafka containing: the meeting ID, the list of affected invitee IDs and emails,
  and the old short code

### Scenario: Password change on scheduled meeting without invitees is a no-op

- **WHEN** a host changes the password on a scheduled meeting that has no
  invitees
- **THEN** the system SHALL update the password normally
- **AND** no `MeetingInviteTokensInvalidatedEvent` SHALL be published (no
  invitees to notify)

### Scenario: No invalidation when password value is unchanged

- **WHEN** a host calls `PUT /api/v1/meetings/{meetingId}/settings` with the
  same `password` as the current value
- **THEN** the system SHALL NOT revoke any invite tokens
- **AND** the system SHALL NOT publish `MeetingInviteTokensInvalidatedEvent`

### Scenario: No invalidation for non-password settings changes

- **WHEN** a host updates meeting settings (e.g., `maxParticipants`,
  `admissionPolicy`) without changing the password
- **THEN** the system SHALL NOT revoke any invite tokens
- **AND** the system SHALL NOT publish `MeetingInviteTokensInvalidatedEvent`

### Scenario: Password change on LIVE meeting does not invalidate tokens

- **WHEN** a host changes the password on a meeting that is `LIVE`
- **THEN** the system SHALL update the password
- **AND** the system SHALL NOT revoke invite tokens (the meeting is already in
  progress; tokens are not the primary join path)

### Scenario: Already-used tokens are not affected by invalidation

- **WHEN** a host changes the password on a scheduled meeting
- **AND** an `InviteToken` has status `USED` or `EXPIRED`
- **THEN** the system SHALL NOT attempt to change the status of that token

## Requirement: Host sees invalidated invite count in settings update response

When a settings update invalidates invite tokens, the response SHALL indicate
how many invitees were affected so the host can decide to resend invites.

### Scenario: Settings update response includes invalidated invite count

- **WHEN** a host successfully changes the password on a scheduled meeting with
  3 pending invitees
- **THEN** the response SHALL include
  `{ "invalidatedInviteCount": 3, "resendInvitesRecommended": true }`

### Scenario: Settings update response for non-password change

- **WHEN** a host updates non-password settings on a scheduled meeting
- **THEN** the response SHALL NOT include `invalidatedInviteCount` or
  `resendInvitesRecommended`

## Requirement: Invitees are notified when their invite link is invalidated

The notification service SHALL consume `MeetingInviteTokensInvalidatedEvent` and
send a "your invite link has been updated" email to each affected invitee,
providing the new join link with the new token.

### Scenario: Affected invitees receive password-change notification email

- **WHEN** `MeetingInviteTokensInvalidatedConsumer` receives a
  `MeetingInviteTokensInvalidatedEvent`
- **AND** the event contains a list of affected invitee emails
- **THEN** for each affected invitee, the notification service SHALL send an
  email with subject "Update: Your meeting invite for [title] has been updated"
- **AND** the email body SHALL include the new join link (the host must have
  resent invites or the email will have a link that requires the host to
  manually share)

### Scenario: Notification service fetches new token for re-send

- **WHEN** `MeetingInviteTokensInvalidatedConsumer` processes an event
- **AND** the host has resent invites (creating new tokens) after the password
  change
- **THEN** the notification service SHALL call
  `GET /api/v1/meetings/{meetingId}/invitees` to retrieve the new token for each
  invitee
- **AND** the re-sent email SHALL include the fresh invite link with the new
  token

### Scenario: Notification service handles partial resend

- **WHEN** the host has resent only some of the invalidated invites
- **THEN** the notification service SHALL only send the password-change
  notification to invitees who have NOT received a new invite (i.e., the host
  has not yet resent to them)
- **AND** invitees who have received a new invite SHALL NOT receive a duplicate
  email

## Requirement: Host workflow after password change

The host-facing meeting settings UI SHALL communicate the impact of a password
change and guide the host to resend invites.

### Scenario: Android settings update shows invalidated invite count

- **WHEN** a host changes the password on a scheduled meeting from the Android
  app
- **AND** the API response indicates `resendInvitesRecommended: true`
- **THEN** the app SHALL show an informational message: "Password changed. X
  pending invites have been invalidated. Resend invites to notify participants."
- **AND** the message SHALL include a "Resend Invites" action button

### Scenario: Host can batch-resend all invalidated invites

- **WHEN** the host taps "Resend Invites" after a password change
- **THEN** the system SHALL call
  `POST /api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend` for each
  invitee with status PENDING
- **AND** the system SHALL show a success confirmation after all resends are
  complete

## Requirement: No automatic resend on password change

The system SHALL NOT automatically resend invites when the password changes. The
host must explicitly choose to resend, to avoid sending unexpected emails to
invitees.

### Scenario: Password change does not trigger automatic resend

- **WHEN** a host changes the password on a scheduled meeting
- **THEN** the system SHALL NOT automatically publish
  `MeetingInvitationsSentEvent` for any invitee
- **AND** the host MUST explicitly call the resend endpoint or add-invitee
  endpoint to trigger new invitation emails

### Scenario: Host can resend all invites individually or via batch operation

- **WHEN** the host wants to notify all invitees of the password change
- **THEN** the host SHALL call the resend endpoint for each invitee individually
- **AND** a future enhancement MAY add a batch resend endpoint
  `POST /api/v1/meetings/{meetingId}/invitees/resend-all`
