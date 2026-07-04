# Why

When a host changes settings on a LIVE meeting, the backend currently persists
the new `MeetingSettings` but does not propagate permission changes to
participants who are already connected to the LiveKit room. This creates a
mismatch between meeting policy and effective media/chat permissions, so the
system needs a complete backend flow that keeps issued tokens and active
participants aligned with the latest settings.

## What Changes

- Extend `MeetingSettingsUpdatedEvent` to carry both the previous and new
  `MeetingSettings` values so downstream handlers can detect meaningful
  permission changes.
- Add an asynchronous `MeetingSettingsChangedHandler` that reacts to LIVE
  meeting settings updates, filters for permission-relevant fields, loads active
  participant sessions, and best-effort syncs LiveKit participant permissions.
- Update LiveKit token generation so PARTICIPANT grants are derived from
  `MeetingSettings`, while HOST remains unrestricted and GUEST remains
  subscribe-only.
- Pass `MeetingSettings` into all token-generation call sites used for direct
  join, manual approval, and bulk approval flows.
- Add or centralize grant computation logic so the same mapping from
  `MeetingSettings` to LiveKit publish/data permissions is reused for token
  issuance and live permission updates.
- Update backend tests to cover enriched events, token grant derivation, and the
  best-effort permission-sync handler.

## Capabilities

### New Capabilities

- `livekit-participant-permissions`: defines how LiveKit media/data grants are
  computed from meeting settings for each participant role and how active live
  sessions are resynchronized after permission-related settings changes.

### Modified Capabilities

- `meeting-settings-replacement-api`: expand successful LIVE settings updates so
  permission-related changes publish enough event context for downstream sync
  and trigger asynchronous participant-permission reconciliation.

## Impact

- Affected backend domain/event code in
  `services/meeting-management/.../domain/event/MeetingSettingsUpdatedEvent.java`
  and `.../domain/model/Meeting.java`.
- Affected application/use-case flow in `PutMeetingSettingsUseCase`,
  `RequestJoinUseCase`, `ApproveJoinRequestUseCase`, and
  `PendingJoinRequestApprover`.
- Affected LiveKit integration contracts and adapter behavior in
  `LiveKitTokenRequest`, `LiveKitPort`, `ParticipantGrants`, and
  `LiveKitAdapter`.
- Affected asynchronous processing and repository usage for active participation
  lookups and best-effort permission updates.
- Affected backend tests for domain events, token generation, and handler
  behavior.
