# Purpose

Define the updated notification service behavior for meeting invitations:
building token-based join links, sending invite emails with per-invitee tokens,
and handling password-change invalidation notifications. This spec replaces the
legacy `short-code + raw-password-in-URL` pattern.

# ADDED Requirements

## Requirement: Build token-based invite join link

The `MeetingInvitationLinkFactory` SHALL build join links using the per-invitee
invite token, not the meeting short code and password.

### Scenario: Join link uses token query parameter

- **WHEN** the notification service needs to build an invite link for an invitee
- **THEN** the `MeetingInvitationLinkFactory` SHALL build the URL as
  `{baseUrl}/join?token={inviteToken}`
- **AND** the URL SHALL NOT contain `shortCode` or `password` as query
  parameters

### Scenario: Fallback link for legacy events

- **WHEN** `MeetingInvitationsSentMessage` is received from a pre-upgrade
  producer that still includes `meetingShortCode` and `rawPassword`
- **AND** the `inviteToken` field is null or empty
- **THEN** the `MeetingInvitationLinkFactory` SHALL fall back to
  `{baseUrl}/join?code={shortCode}&password={rawPassword}`
- **AND** a warning log SHALL be emitted indicating a legacy event was processed

## Requirement: Send invite email on meeting schedule

The notification service SHALL consume `MeetingInvitationsSentEvent` and send
one invitation email per invitee, with the invite link containing the
per-invitee token.

### Scenario: Invite email sent to each invitee with token-based link

- **WHEN** `MeetingInvitationsSentConsumer` receives
  `MeetingInvitationsSentEvent`
- **AND** the event contains a non-empty list of invitees
- **THEN** for each `InviteeInfo` in the event, the consumer SHALL call
  `SendMeetingInvitationEmailUseCase.send(message, invitee)`
- **AND** the email SHALL include the invite link built from
  `MeetingInvitationLinkFactory.buildInviteLink(shortCode, null)`
- **AND** the email body SHALL NOT contain the meeting password in any form

### Scenario: `rawPassword` field removed from Kafka event

- **WHEN** the notification service deserializes `MeetingInvitationsSentMessage`
- **AND** the `rawPassword` field is present (from legacy producer during
  migration)
- **THEN** the service SHALL ignore the field and SHALL NOT use it in email
  rendering

### Scenario: Individual invitee processing failures are isolated

- **WHEN** `MeetingInvitationsSentConsumer` is processing a batch of invitees
- **AND** sending fails for invitee N but succeeds for invitees 1..N-1
- **THEN** the failure for invitee N SHALL NOT roll back the successful sends
  for other invitees
- **AND** the failure SHALL be logged with the invitee's email and event ID

## Requirement: Send password-change notification to invalidated invitees

The notification service SHALL consume `MeetingInviteTokensInvalidatedEvent` and
send a "your invite link has been updated" email to each affected invitee.

### Scenario: Password-change notification email sent to affected invitees

- **WHEN** `MeetingInviteTokensInvalidatedConsumer` receives an event with a
  list of affected invitees
- **AND** the host has already resent invites (new tokens exist) for some or all
  of the affected invitees
- **THEN** the consumer SHALL call the meeting-management API to retrieve
  updated invitee data with new tokens
- **AND** for each affected invitee with a new token, the consumer SHALL send an
  email with subject "Update: Your meeting invite for [title] has been updated"
- **AND** the email body SHALL include the fresh invite link with the new token

### Scenario: No duplicate emails if host already resent

- **WHEN** `MeetingInviteTokensInvalidatedConsumer` processes an event
- **AND** the host has already resent the invite to an invitee (new
  `MeetingInvitationsSentEvent` was published)
- **THEN** the consumer SHALL check whether a new invite email has already been
  sent before sending the password-change notification
- **AND** if a new invite email was sent after the invalidation event, the
  consumer SHALL skip sending the password-change notification for that invitee

### Scenario: No notification sent if no new tokens are available

- **WHEN** `MeetingInviteTokensInvalidatedConsumer` processes an event
- **AND** the host has not resent any invites (no new tokens exist)
- **THEN** the consumer SHALL log a warning
- **AND** no email SHALL be sent (the host must resend invites manually)

## Requirement: Meeting cancellation notification includes invite status context

When a meeting is cancelled, the existing cancellation email flow remains
unchanged.

### Scenario: Cancellation email still sent via existing consumer

- **WHEN** `MeetingCancelledConsumer` receives `MeetingCancelledEvent`
- **THEN** the existing behavior SHALL continue unchanged
- **AND** cancelled invites SHALL NOT trigger password-change or invite-resend
  emails

## Requirement: Invite email template updated to remove password reference

The `MeetingInvitationEmailRenderer` SHALL be updated to remove any reference to
the meeting password from the invite email template.

### Scenario: Invite email no longer mentions password

- **WHEN** `MeetingInvitationEmailRenderer` generates an invite email
- **THEN** the email body SHALL NOT contain the words "password", "pwd", or any
  credential field
- **AND** the call-to-action SHALL be a single "Join Meeting" button linking to
  the token-based invite URL
- **AND** if a fallback legacy URL is used, the button text SHALL still say
  "Join Meeting"

### Scenario: Invite email includes meeting title and start time

- **WHEN** an invite email is rendered
- **THEN** the email body SHALL include the meeting title (or "Your meeting" as
  fallback)
- **AND** the email body SHALL include the scheduled start time formatted in the
  invitee's local timezone
- **AND** the email SHALL NOT include the meeting short code as plain text
