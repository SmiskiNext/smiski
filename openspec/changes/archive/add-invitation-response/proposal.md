## Why

Invitees currently receive meeting invitation emails with a join link, but they
have no way to explicitly accept or decline the invitation before the meeting
starts. The `MeetingInvitee` domain model already supports `accept()` and
`decline()` transitions with domain events, but no API endpoint or client
integration exposes this to the invitee. Adding accept/decline capability
enables hosts to see RSVP status and plan accordingly.

## What Changes

- New backend use case `RespondInviteUseCase` that transitions invitee status to
  ACCEPTED or DECLINED
- New REST endpoint for invitees to respond to invitations (authenticated,
  invitee-facing)
- New Kafka consumer in notification service to notify the host when an invitee
  accepts/declines
- Android app: new notification action buttons (Accept/Decline) and a pending
  invitations section on the dashboard
- OpenAPI spec update to include the new endpoint, triggering SDK regeneration
  for Android

## Capabilities

### New Capabilities

- `invitation-response`: Backend API endpoint and use case for invitees to
  accept or decline meeting invitations, including domain event publishing and
  host notification

### Modified Capabilities

- `invite-notification-v2`: Add host-facing notification when an invitee
  responds (accept/decline) to their invitation

## Impact

- `services/meeting-management`: New use case, command, controller endpoint,
  tests
- `services/notification`: New Kafka consumer for invitee.accepted/declined
  topics, email renderer for host notification
- `frontends/android-app`: New API call in MeetingRepository, dashboard pending
  invitations UI, notification action handling
- `openapi/unified-openapi.yaml`: New endpoint added to spec
- Generated SDK files for Android will be regenerated
