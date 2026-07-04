## Context

The meeting-management service has a `MeetingInvitee` aggregate with `accept()`
and `decline()` domain methods that transition status (`PENDING -> ACCEPTED`,
`PENDING -> DECLINED`, `ACCEPTED -> DECLINED`) and register
`InviteeAcceptedEvent`/`InviteeDeclinedEvent`. These domain events already have
Kafka topic mappings (`meeting-management.invitee.accepted`,
`meeting-management.invitee.declined`). However, no application use case, no
controller endpoint, and no client integration exists to trigger these
transitions from the invitee's side.

Currently, invitees receive an email with a join link (`/join?token=...`). The
`ValidateInviteTokenUseCase` marks the token as USED and returns meeting details
for joining, but does not change the invitee's RSVP status. There is no separate
RSVP flow — invitees can only "respond" by showing up or not.

The Android app has a deep link flow for invite tokens via `SplashActivity` and
uses generated Retrofit API interfaces from the unified OpenAPI spec.

## Goals / Non-Goals

**Goals:**

- Allow authenticated invitees to accept or decline a meeting invitation via a
  REST endpoint
- Publish domain events on accept/decline so the host can be notified
- Consume `InviteeAcceptedEvent` / `InviteeDeclinedEvent` in the notification
  service to send host notification emails
- Add Android client integration: pending invitations list on dashboard with
  accept/decline actions

**Non-Goals:**

- Push notifications (FCM) — out of scope; only email notification to host
- Web frontend integration — separate change
- Revoking acceptance (ACCEPTED -> PENDING) — not supported by domain model
- Anonymous/unauthenticated respond (token-only RSVP) — invitee must be logged
  in

## Decisions

### 1. Endpoint Design: Invitee-facing PATCH endpoint

**Decision**: `PATCH /api/v1/meetings/{meetingId}/invitees/me` with body
`{ "response": "ACCEPTED" | "DECLINED" }`.

**Rationale**: The invitee acts on their own invitation (identified by auth
token → userId matched against `MeetingInvitee.userId`). Using `/me` avoids
exposing internal inviteeId to the client and follows the existing pattern of
user-scoped endpoints.

**Alternative considered**: Using the invite token as authorization
(unauthenticated endpoint). Rejected because the token is already consumed
during join flow (`markUsed`) and cannot be reused.

### 2. Invitee Lookup: By userId + meetingId

**Decision**: The use case finds the invitee by `meetingId` + authenticated
`userId` from the JWT.

**Rationale**: Each invitee is resolved via gRPC to a userId at creation time.
The repository already supports `findByMeetingId(UUID)` — we add a
`findByMeetingIdAndUserId(UUID, UUID)` query method to the repository port and
adapter.

### 3. Host Notification: Kafka consumer in notification service

**Decision**: Add a single `InviteeRespondedConsumer` that listens to both
`meeting-management.invitee.accepted` and `meeting-management.invitee.declined`
topics. It sends an email to the host with the invitee's response.

**Rationale**: Follows the existing pattern used by
`MeetingInvitationsSentConsumer` and `MeetingCancelledNotificationConsumer`.
Separate consumer for clarity.

### 4. Android: Pending Invitations on Dashboard

**Decision**: Add a "Pending Invitations" section above "Upcoming Meetings" on
the dashboard. Each card shows meeting title, host name, time, and
Accept/Decline buttons. Uses a new
`GET /api/v1/users/{userId}/invitations:pending` endpoint.

**Rationale**: The dashboard is the first screen after login and the natural
place to surface pending invitations. The new endpoint returns only PENDING
invitations for the authenticated user.

### 5. New Backend Endpoint: Get Pending Invitations for User

**Decision**: `GET /api/v1/users/{userId}/invitations:pending` returns a list of
pending invitations with meeting metadata (title, host display name, start time,
meeting ID).

**Rationale**: The Android dashboard needs to display pending invitations with
meeting context. A dedicated endpoint avoids N+1 queries on the client side.

## Risks / Trade-offs

- **[Risk] Invitee without userId**: If an invitee was created without a
  resolved userId (gRPC failure at invite time), they cannot use this endpoint.
  → **Mitigation**: This is an edge case; the existing `AddInviteeUseCase`
  already fails fast if user resolution fails (returns `InviteeNotFound`), so
  all persisted invitees have a userId.
- **[Risk] Race condition with ValidateInviteToken**: An invitee could decline
  but still use the join link (separate flow). → **Mitigation**: Accept this as
  user choice; the join flow checks meeting status and waiting room, not invitee
  RSVP status. The RSVP is informational for the host.
- **[Trade-off] Email-only host notification**: No real-time SSE push to the
  host for RSVP updates. → **Accepted**: Host SSE currently only handles
  join_request and participant_kicked events. Adding RSVP events to SSE is
  future work.
