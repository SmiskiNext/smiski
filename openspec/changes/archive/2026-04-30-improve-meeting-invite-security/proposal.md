# Why

The current meeting invite flow embeds the meeting's raw password as a URL query
parameter in invitation emails sent via Kafka. This leaks the password through
multiple attack surfaces: the Kafka event payload, email subject/body, browser
history, server logs, and shared link copies. Additionally, invite links carry
no expiry and no per-invitee token, making it impossible to revoke individual
invitations or respond to compromised credentials without full meeting
rescheduling. When a host changes a scheduled meeting's password, existing
invites silently continue working with the old credential, leaving the host with
no visibility or control.

# What Changes

- Replace the password-in-URL pattern with cryptographically signed,
  time-limited per-invitee invite tokens embedded in the join link.
- Remove the raw password from the `MeetingInvitationsSentEvent` Kafka payload.
- Store invite token metadata (token ID, invitee, expiry, status) alongside
  `MeetingInvitee` to support per-invitee revocation and resend.
- When the host changes the password on a scheduled (not-yet-started) meeting,
  automatically invalidate all existing PENDING invite tokens and fire a
  `InviteTokensInvalidatedEvent` so the notification service can warn invitees.
- Add a `POST /meetings/invite-tokens/validate` endpoint that accepts a token
  string and returns whether it grants join access, without revealing sensitive
  meeting details.
- Add a `GET /meetings/{id}/invitees` endpoint for hosts to list invitees with
  their token status.
- Add a `POST /meetings/{id}/invitees/{inviteeId}/resend` endpoint to regenerate
  and re-send an invite token.
- Add a `DELETE /meetings/{id}/invitees/{inviteeId}` endpoint to revoke an
  individual invite.
- Update the notification service to use the new token-based join URL and remove
  password handling.

# Capabilities

### New Capabilities

- `invite-token-generation`: Generation, signing, storage, and validation of
  per-invitee HMAC-SHA256 invite tokens tied to a specific meeting and invitee
  email, with configurable expiry.
- `join-by-invite-token`: New backend API endpoint that accepts an invite token
  and grants a join token (LiveKit) without requiring a separate password entry
  step.
- `invite-management`: Host-facing REST API for listing, resending, and revoking
  invite tokens on a per-invitee basis.
- `host-password-change-invite-invalidation`: Automatic invalidation of all
  PENDING invite tokens when the host changes a scheduled meeting's password,
  with notification to affected invitees.
- `invite-notification-v2`: Updated notification service behavior: builds
  token-based join links, sends one invite email per invitee with the correct
  token, and handles password-change notifications.

### Modified Capabilities

- `meeting-schedule-invite-flow` (backend scheduling): The existing
  `ScheduleMeetingCommand` and `ScheduleMeetingUseCase` no longer pass
  `rawPassword` to `MeetingInvitationsSentEvent`. Invitee creation now also
  creates and stores `InviteToken` records. This changes the
  `POST /meetings:schedule` API contract only if the caller was relying on
  password data in downstream Kafka events.

# Impact

**Backend (meeting-management service)**:

- New domain model: `InviteToken` (value object / aggregate),
  `InviteTokenStatus` enum.
- New JPA entity `invite_tokens` table with migration.
- New `InviteTokenRepository` port and adapter.
- New `InviteTokenService` (token generation/validation).
- Changes to `ScheduleMeetingUseCase`: generate tokens, remove
  `invitationRawPassword` from event.
- Changes to `PutMeetingSettingsUseCase`: detect password change on SCHEDULED
  meeting, invalidate tokens, publish `InviteTokensInvalidatedEvent`.
- New REST endpoints: `GET /meetings/{id}/invitees`,
  `POST /meetings/{id}/invitees/{id}/resend`,
  `DELETE /meetings/{id}/invitees/{id}`,
  `POST /meetings/invite-tokens/validate`.
- New Kafka event: `InviteTokensInvalidatedEvent`.

**Backend (notification service)**:

- Remove `rawPassword` from `MeetingInvitationsSentMessage` consumer (already
  does not use it directly but the record shape will change).
- Update `MeetingInvitationLinkFactory` to build a token-based URL instead of a
  short-code + password URL.
- New Kafka consumer: `InviteTokensInvalidatedConsumer` sends "meeting security
  updated" notification to affected invitees.
- New API call from notification service: `GET /meetings/{id}/invitees` to look
  up invitee emails for invalidation events.

**Database**:

- Flyway migration: new `invite_tokens` table (id, meeting_id, invitee_id,
  token_hash, status, expires_at, created_at, updated_at).
- `meeting_invitees` table gains `invite_token_id` foreign key.

**API / OpenAPI**:

- Updated `ScheduleMeetingRequest` and response DTOs no longer surface password
  in invite data.
- New endpoints added to OpenAPI spec.

**Android app**:

- Join flow must handle invite token URL: when a user opens an invite link,
  extract the token and call `POST /meetings/invite-tokens/validate` followed by
  the existing join flow.
- Host invite management UI: list invitees, see token status (PENDING / REVOKED
  / USED / EXPIRED), resend invite, revoke invite.
- `ScheduleFragment`: no change to the create/schedule flow itself; invitees are
  still added by email.

**Web app**: Same token-based join link handling as Android.
