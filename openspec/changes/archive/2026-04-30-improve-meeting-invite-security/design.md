# Context

## Background and Current State

When a host schedules a meeting with invitees, the `ScheduleMeetingUseCase`
creates `MeetingInvitee` records and publishes a `MeetingInvitationsSentEvent`
to Kafka. This event carries `meetingShortCode` and `rawPassword` in plaintext.
The notification service's `MeetingInvitationLinkFactory` then builds a join URL
as `https://app.example.com/join?code={shortCode}&password={rawPassword}`, which
is emailed to each invitee.

**Problems with the current design:**

1. **Password in Kafka**: `rawPassword` travels unencrypted through Kafka
   message payloads. Any service or consumer with access to the topic can read
   it.
2. **Password in URLs**: The invite link contains the raw password as a query
   parameter. It gets stored in browser history, email logs, CDN/access logs,
   and any intermediate proxy.
3. **No per-invitee tokens**: All invitees for the same meeting receive the same
   short-code + password combination. Revoking one invitee requires changing the
   meeting password and resending to all.
4. **No token expiry**: Invite links are valid indefinitely until the meeting
   starts, ends, or the password changes.
5. **No invite token revocation API**: The system has no endpoint for hosts to
   manage individual invites (revoke, resend, list status).
6. **Password change silently preserves invites**: `PutMeetingSettingsUseCase`
   updates the password but `MeetingInvitationsSentEvent` is not re-published
   for SCHEDULED meetings. Existing invitees holding old invite links can still
   join with the old password.
7. **No notification on password change**: When a host changes the password,
   pending invitees receive no alert and may attempt to join with an outdated
   password or stale link.

## Constraints

- The backend is Spring Boot (Java 25) using Clean Architecture.
- Domain events use the Transactional Outbox pattern over Kafka (CloudEvents 1.0
  binary mode).
- The shared module provides `Result<T, E>`, `AggregateRoot`, and `ValueObject`
  base classes.
- Meetings use UUIDv7 time-ordered IDs; short codes are human-readable
  8-character alphanumeric strings.
- The notification service runs as a separate microservice consuming Kafka
  events.
- Android and Web apps are the primary clients; they must handle token-based
  join URLs.

## Stakeholders

- Meeting hosts (need control over who can join, ability to revoke)
- Meeting invitees (need a simple, secure join experience)
- Security / compliance team (no passwords in logs/URLs/Kafka)
- Backend and frontend developers (clear API contracts)

## Goals / Non-Goals

**Goals:**

- Eliminate plaintext passwords from Kafka, URLs, and email content in the
  invite flow.
- Provide per-invitee, time-limited invite tokens that the host can
  independently revoke or resend.
- Handle host password changes on scheduled meetings by automatically
  invalidating pending invite tokens and notifying affected invitees.
- Maintain backward compatibility for the core meeting lifecycle APIs (schedule,
  start, join, end).

**Non-Goals:**

- Replacing the short code system entirely (short codes remain the
  human-friendly meeting identifier used for manual join).
- Implementing OAuth or external identity federation for invite links.
- Supporting multi-meeting invite links (one token for recurring meetings).
- Adding meeting recording access tokens (separate concern).
- Re-architecting the notification delivery infrastructure beyond the
  invite-flow changes.

## Decisions

### Decision 1: Token format — HMAC-SHA256 signed, not opaque random string

**Choice**: Use an HMAC-SHA256 signed token of the form
`base64(H) | meetingId | inviteeId | expiryEpoch` where
`H = HMAC-SHA256(meetingId | inviteeId | expiryEpoch, serverSecret)`.

**Rationale**: A purely random opaque token (e.g., a UUID) requires a database
lookup on every validation request, adding latency and a database dependency to
the hot validation path. A signed token avoids storage for the happy path: the
server can verify authenticity and expiry by recomputing the HMAC. The token
still references `meetingId` and `inviteeId` so the validation endpoint knows
which meeting the user is trying to join without an upfront DB lookup to resolve
token → meeting.

**Alternatives considered**:

- _Opaque random UUID_: Simpler to implement but requires `invite_tokens` table
  lookup on every validation. A `SELECT` against an indexed table is cheap but
  adds coupling between the token service and the repository in the hot path.
- _JWT_: Standard but heavyweight; adds a library dependency and exposes claims
  that may include meeting metadata. The HMAC token is intentionally opaque.
- _GUID alone_: Insecure — trivially guessable given the UUIDv7 format.

**Implementation detail**: Store `tokenHash = SHA-256(rawToken)` in the DB for
revocation lookups. The raw token is only ever shown to the invitee once (in the
email). The hash enables revocation without storing the raw token.

### Decision 2: Token scope — per invitee, tied to invitee email

**Choice**: One invite token per `MeetingInvitee` record, locked to the
invitee's email address.

**Rationale**: Tying the token to the invitee email prevents token leakage from
being used by a different person (unless the email account is also compromised).
The `MeetingInvitee` already has the invitee's `UserId` resolved, which allows
us to pre-approve known users without a separate join request step.

**Alternatives considered**:

- _Per meeting (one token for all invitees)_: Revoking one invitee requires
  changing the token and resending to everyone. Rejected.
- _Anonymous tokens (no email binding)_: Anyone with the token could join, even
  if they are not the intended invitee. Rejected.

### Decision 3: Token expiry — 7 days from invite send time

**Choice**: Invite tokens expire 7 days after creation. The host can resend to
extend expiry.

**Rationale**: 7 days covers typical scheduling workflows (send Friday for a
Monday meeting, or weeks ahead for important meetings). Shorter expiry (e.g.,
24h) is too restrictive for meetings scheduled far in advance. Longer expiry
increases window of token leakage risk. The host can always resend to regenerate
a fresh token.

**Alternatives considered**:

- _Expire at meeting start_: Leaves active tokens after a rescheduled meeting.
  Can be added as a future enhancement.
- _No expiry_: Not acceptable from a security standpoint.

### Decision 4: Token invalidation on password change

**Choice**: When the host changes the password on a SCHEDULED meeting (not
LIVE), all PENDING `InviteToken` records are marked REVOKED and a
`MeetingInviteTokensInvalidatedEvent` is published. The notification service
sends a "your invite link has been updated" email to affected invitees with the
new invite link.

**Rationale**: The previous behavior (tokens silently surviving a password
change) is confusing and a security gap — someone who received the old invite
could share it with unauthorized people, and the host would have no way to know.
Auto-invalidation makes the security change explicit and gives invitees a fresh,
correct link.

**Alternatives considered**:

- _Require host to explicitly choose whether to invalidate tokens_: Adds UI
  complexity and a security trap (hosts may not understand the implications).
  The default should be the secure option.
- _Keep old tokens valid alongside new password_: Defeats the purpose of
  invalidation. Rejected.
- _Only notify invitees, don't invalidate_: Partial fix. The invitee email may
  be forwarded, and the stale link remains valid. Rejected.

### Decision 5: Join flow for invite token holders

**Choice**: `POST /meetings/invite-tokens/validate` accepts `{ token: "..." }`
and returns
`{ valid: true/false, meetingId, shortCode, passwordRequired: false, requiresJoinRequest: boolean }`.
The client then proceeds with the normal join flow using the returned
`shortCode`. If `passwordRequired` is false (the invite link carries join
authority), the client skips the password prompt.

**Rationale**: This keeps the join flow consistent — the invite token grants
pre-authentication, but the rest of the join process (LiveKit token generation,
participant limits, waiting room policy) uses existing mechanisms. No new
permission model needed.

**Alternatives considered**:

- _Issue a short-lived JWT in the validate response_: Adds complexity; the
  existing LiveKit token flow already handles short-lived session tokens.
- _Return a direct LiveKit token from validate_: Couples the token validation to
  LiveKit provisioning. Rejected — separate concerns.

### Decision 6: Kafka event changes — remove rawPassword from MeetingInvitationsSentEvent

**Choice**: Remove `rawPassword` from `MeetingInvitationsSentEvent` entirely.
The event continues to carry `meetingShortCode` (needed for the fallback join
link for users who cannot use the invite token URL) and the per-invitee token
string.

**Rationale**: `meetingShortCode` is not sensitive — it is shown in the meeting
UI and is already publicly enumerable. `rawPassword` is sensitive and must not
be in the event. The notification service already computes the invite link
separately via `MeetingInvitationLinkFactory`; this factory will be updated to
use the per-invitee token instead.

**Migration concern**: Any downstream consumer of `MeetingInvitationsSentEvent`
that was reading `rawPassword` will now receive `null`. The consumer in the
notification service will be updated in the same commit, so this is a
coordinated change.

### Decision 7: Database schema — extend meeting_invitees with invite_token_id

**Choice**: Add a `invite_token_id` UUID column to `meeting_invitees`. The
`invite_tokens` table holds
`(id, meeting_id, invitee_id, token_hash, status, expires_at, created_at, updated_at)`.

**Rationale**: The `InviteToken` is tightly associated with a `MeetingInvitee` —
one token per invitee. Storing the FK on the invitee side keeps the token lookup
path simple (join through invitee). The token hash enables revocation without
storing the raw token value.

## Risks / Trade-offs

[Risk] HMAC secret key management

- The HMAC signing key must be kept secret. If leaked, attackers can forge
  invite tokens.
- **Mitigation**: Store the key in a secrets manager (Vault, AWS Secrets
  Manager, Kubernetes Secret) and inject via environment variable. Rotate on a
  schedule. If rotation occurs mid-flight, existing tokens signed with the old
  key will fail validation — hosts can resend invites to regenerate tokens.

[Risk] Email forwarding of invite links

- Invite links are emailed; invitees may forward them. This is inherent to
  email-based invites.
- **Mitigation**: The token is scoped to the invitee's email address. The
  `validate` endpoint can optionally check that the user claiming the token is
  authenticated as the invitee (using the userId from the token). For now, the
  token grants the join authority without an additional auth check on the email
  address — this matches the current short-code + password behavior.

[Risk] Clock skew on token expiry

- Tokens use epoch seconds for expiry; clients and servers with significant
  clock skew may disagree on whether a token is expired.
- **Mitigation**: Allow a 60-second grace period when checking expiry
  server-side.

[Risk] Backward compatibility for existing short-code + password join flow

- The existing `GET /meetings:byShortCode?code=X` and `POST /meetings/{id}/join`
  endpoints with password still exist. They are not removed.
- **Mitigation**: The token-based flow is additive. Short-code + password join
  continues to work for hosts who share credentials manually.

[Risk] Rollout order

- If the notification service is updated before the meeting-management service,
  invitation emails will have broken links.
- **Mitigation**: Coordinate the deployment of both services. Use a feature flag
  (`invite.use-tokens=true`) on the meeting-management service so the new flow
  can be enabled after both services are deployed.

[Risk] Token revocation is eventual

- Marking a token as REVOKED in the DB does not immediately prevent use if the
  token was already validated and the client is in-session.
- **Mitigation**: Token revocation affects future validation attempts only. For
  active sessions, use existing LiveKit permission revocation mechanisms. This
  is the same limitation as password rotation today.

## Migration Plan

### Phase 1 — New token infrastructure (backward compatible)

1. Add `InviteToken` domain model, `InviteTokenRepository` port, and JPA adapter
   with Flyway migration.
2. Add `InviteTokenService` (generate, validate, revoke).
3. Update `ScheduleMeetingUseCase` to create `InviteToken` alongside
   `MeetingInvitee`. Feature flag `invite.use-tokens=false` by default.
4. Update `MeetingInvitationsSentEvent` to include per-invitee token string;
   keep `rawPassword` for backward compat.
5. Deploy meeting-management service.

### Phase 2 — Notification service update (backward compatible)

1. Update `MeetingInvitationLinkFactory` to build token-based URL when token is
   present; fall back to short-code + password URL for old events.
2. Deploy notification service.

### Phase 3 — Remove rawPassword from events (opt-in)

1. Set feature flag `invite.use-tokens=true`.
2. Remove `rawPassword` from `MeetingInvitationsSentEvent` and
   `MeetingInvitationsSentMessage`.
3. Update `ScheduleMeetingUseCase` to stop passing `rawPassword` to the event.
4. Remove backward-compatible fallback in notification service.
5. Deploy both services.

### Phase 4 — Password change invalidation

1. Update `PutMeetingSettingsUseCase` to detect password changes on SCHEDULED
   meetings.
2. Add `InviteTokensInvalidatedEvent` and `InviteTokensInvalidatedConsumer` in
   notification service.
3. Add `POST /meetings/{id}/invitees/{inviteeId}/resend` and `DELETE` endpoints.
4. Deploy both services.

### Rollback

- Revert the feature flag to `invite.use-tokens=false`. Old Kafka event
  consumers that read `rawPassword` will receive it again from the updated
  `ScheduleMeetingUseCase`.

## Open Questions

1. Should the invite token also carry the `UserId` of the resolved invitee so
   the `validate` endpoint can pre-approve the user (skip waiting room if they
   are a registered user)?
    - _Tentative answer_: Yes. Add `userId` to the signed payload. The
      `validate` response indicates whether the token holder is pre-approved
      based on `admissionPolicy`.

2. Should invite tokens be usable after the meeting starts?
    - _Tentative answer_: Yes, until the meeting ends. The expiry is 7 days from
      invite send, independent of meeting timing. Post-meeting, tokens are
      irrelevant.

3. Should we add a `POST /meetings/{id}/invitees` endpoint for adding invitees
   to an already-scheduled meeting (re-invite)?
    - _Tentative answer_: Yes, as part of the invite management capability.
      Scope it to SCHEDULED meetings only.

4. What happens to invite tokens when a meeting is cancelled?
    - _Tentative answer_: All tokens for that meeting are implicitly invalid
      (meeting no longer exists). No explicit invalidation needed; the
      `validate` endpoint will return MEETING_NOT_FOUND. A
      `MeetingCancelledEvent` already exists and the notification service sends
      cancellation emails.

5. Should we emit a new `InviteTokenCreatedEvent` so the notification service
   can send invites asynchronously without coupling to
   `MeetingInvitationsSentEvent`?
    - _Tentative answer_: No, keep invite token creation and notification
      sending coupled in `ScheduleMeetingUseCase`. The
      `MeetingInvitationsSentEvent` carries the token data and serves as the
      trigger.
