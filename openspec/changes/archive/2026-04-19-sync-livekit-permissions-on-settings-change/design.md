# Context

`PUT /api/v1/meetings/{id}/settings` already replaces a meeting's persisted
settings and publishes `MeetingSettingsUpdatedEvent`, but the event only carries
metadata and no before/after settings snapshot. As a result, no downstream
component can tell whether a LIVE meeting's participant media/chat permissions
changed, and already connected participants keep the permissions they had when
their token was issued.

The LiveKit integration already supports two relevant behaviors:

- token issuance through `LiveKitPort.generateToken(...)`
- mid-session permission updates through
  `LiveKitPort.updateParticipantPermissions(...)`

However, current token generation derives grants only from `ParticipantRole`,
and the mid-session update path is only used for other runtime permission flows.
This change is cross-cutting across domain events, application event handling,
LiveKit grant mapping, and join approval flows, so a design document helps lock
down one consistent permission model.

## Goals / Non-Goals

**Goals:**

- Publish enough context in `MeetingSettingsUpdatedEvent` to compare previous
  and current permission-related settings.
- Ensure newly issued PARTICIPANT LiveKit tokens honor the meeting's current
  `allowMicrophone`, `allowVideo`, `allowScreenShare`, and `chatEnabled`
  settings.
- Ensure already connected live PARTICIPANT sessions are updated asynchronously
  after permission-relevant settings changes.
- Reuse one shared mapping from `MeetingSettings` + `ParticipantRole` to LiveKit
  grants so token issuance and runtime synchronization stay consistent.
- Preserve current special cases where HOST keeps full permissions and GUEST
  remains subscribe-only.
- Keep settings updates best-effort for live participant sync so one failing
  LiveKit update does not abort the rest of the reconciliation.

**Non-Goals:**

- Changing Android or web client behavior for handling LiveKit permission-change
  callbacks.
- Changing token TTL semantics or reissuing tokens for already connected users.
- Expanding the permission model beyond microphone, camera, screen share, and
  chat.
- Synchronizing permissions for users who are not currently active in the room.
- Restricting HOST permissions based on meeting settings.

## Decisions

### D1: Enrich the settings-updated event with full before/after settings

**Decision:** Add `oldSettings` and `newSettings` to
`MeetingSettingsUpdatedEvent`, and update `Meeting.updateSettings()` to capture
the prior value before replacing the aggregate's settings.

**Rationale:** The handler only needs to run when permission-relevant fields
actually changed. Carrying both snapshots in the event keeps that comparison
close to the source of truth and avoids a second repository read or ambiguous
reconstruction after the update commits.

**Implications:**

- Domain-event tests and use-case tests must assert the new payload fields.
- The event type/topic remain unchanged, so downstream consumers can keep the
  same channel while reading richer payloads.
- For unchanged settings values, the handler can exit early without touching
  LiveKit.

**Alternatives considered:**

- Re-read the meeting in the handler and infer differences from other state →
  rejected because the old settings are already lost after persistence.
- Add only `newSettings` to the event → rejected because it cannot support
  precise changed-field detection.

### D2: Centralize permission derivation in `ParticipantGrants`

**Decision:** Extend `ParticipantGrants` with a factory such as
`fromSettings(MeetingSettings settings, ParticipantRole role)` that becomes the
canonical mapping for token issuance and runtime updates.

**Rationale:** The codebase already has a domain value object for runtime
grants. Using it as the shared source avoids duplicating permission logic in
`LiveKitAdapter` and keeps the HOST / PARTICIPANT / GUEST policy explicit in one
place.

**Implications:**

- HOST maps to unrestricted publish/data/subscribe grants regardless of meeting
  settings.
- GUEST maps to subscribe-only regardless of meeting settings.
- PARTICIPANT maps to subscribe=true, `canPublishData = chatEnabled`, and
  `canPublish = true` only when at least one publishable source remains enabled.
- The value object may need to grow source-specific information, or the token
  generation path must combine `ParticipantGrants` with source filtering logic.

**Alternatives considered:**

- Keep the logic private inside `LiveKitAdapter` → rejected because the same
  mapping is needed by both `generateToken` and `updateParticipantPermissions`.
- Create a separate application-level utility → rejected because the mapping is
  domain policy, not adapter glue.

### D3: Use source-aware token generation for PARTICIPANT only

**Decision:** Add `MeetingSettings` to `LiveKitTokenRequest` and update
`LiveKitAdapter.generateToken()` so PARTICIPANT tokens compute both boolean
grants and `canPublishSources` from meeting settings, while HOST and GUEST keep
their current fixed behavior.

**Rationale:** LiveKit token permissions must match the same policy enforced for
connected users; otherwise newly joining participants would receive more rights
than those updated mid-session. The source list is required because disabling
one publish capability (for example camera) must still allow other enabled
sources such as microphone.

**Implications:**

- All token-generation call sites must pass `meeting.getSettings()`.
- PARTICIPANT token generation must exclude `microphone`, `camera`,
  `screen_share`, and `screen_share_audio` according to the meeting settings.
- When all media sources are disabled, PARTICIPANT tokens must set
  `canPublish=false`.

**Alternatives considered:**

- Continue issuing broad participant tokens and rely only on runtime sync →
  rejected because first-join behavior would remain inconsistent.
- Recompute permissions only in use cases before calling the adapter → rejected
  because the adapter already owns LiveKit-specific grant translation.

### D4: Reconcile active sessions asynchronously after commit

**Decision:** Introduce `MeetingSettingsChangedHandler` as an asynchronous event
listener for `MeetingSettingsUpdatedEvent` that runs after the settings update
is committed, processes only LIVE meetings, filters for permission-relevant
field changes, and iterates active `ParticipationLog` entries for `PARTICIPANT`
roles.

**Rationale:** Settings updates should not block on multiple network calls to
the LiveKit server. The event-driven approach fits the existing Spring
application event pattern and allows best-effort fan-out to active sessions
without changing the API response contract.

**Implications:**

- The handler will use `ParticipationLogRepository.findActiveByMeetingId(...)`
  and skip HOST and GUEST sessions.
- For each active PARTICIPANT, it will call
  `LiveKitPort.updateParticipantPermissions(...)` using the same computed grants
  derived from the new settings.
- Failures are logged per participant and processing continues for the remaining
  sessions.
- If no permission-relevant field changed, the handler exits without repository
  or LiveKit work.

**Alternatives considered:**

- Perform LiveKit updates inline in `PutMeetingSettingsUseCase` → rejected
  because it would slow or destabilize the host settings-update request.
- Update all active roles including HOST and GUEST → rejected because their
  policies are intentionally fixed.

## Risks / Trade-offs

- **Richer event payload changes serialized event shape** → Mitigation: keep the
  event type/topic stable and only add fields, which is additive for internal
  consumers.
- **Token and runtime permission paths could drift again** → Mitigation:
  centralize mapping in one grant factory and cover it with focused tests.
- **Best-effort sync may leave a subset of active participants stale after
  transient LiveKit failures** → Mitigation: log each failure with meeting and
  identity context so operators can diagnose; future retry logic can build on
  the same handler if needed.
- **LiveKit permission API supports booleans plus source filtering, which may
  not map one-to-one with the current `ParticipantGrants` record** → Mitigation:
  keep boolean grant intent in `ParticipantGrants` and let the token path add
  the required source subset from `MeetingSettings`.

## Migration Plan

1. Extend `MeetingSettingsUpdatedEvent` and update aggregate/use-case tests for
   the enriched payload.
2. Add shared grant derivation from `MeetingSettings` and update
   `LiveKitTokenRequest` plus all token call sites.
3. Update `LiveKitAdapter.generateToken()` to apply source restrictions and chat
   restrictions for PARTICIPANT tokens.
4. Add `MeetingSettingsChangedHandler` with LIVE-only, changed-fields-only,
   best-effort synchronization over active participant sessions.
5. Add or update tests for token grant computation, event publication, and
   handler behavior.
6. Verify with `./gradlew :services:meeting-management:test`.

Rollback is a code revert. No persistence migration is required because the
change only enriches events and changes runtime permission behavior.

## Open Questions

- None for artifact creation. The permission matrix, affected roles, and
  out-of-scope items are specific enough to proceed.
