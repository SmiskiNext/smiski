# Context

The meeting-management service exposes participant management endpoints via
`ParticipantController`, following a hexagonal architecture with
`Result<T, MeetingError>` error handling throughout. The host can already kick
participants via `KickParticipantUseCase`, which establishes the auth-check and
LiveKit-operation pattern to follow.

The Android app has a stub `muteAllParticipants()` in `CallViewModel` (marked
TODO) and a hidden/disabled `btnMuteAll` in `ParticipantsBottomSheet`.
`ParticipantAdapter` currently renders mic/camera state as read-only indicators.

LiveKit's `RoomServiceClient` supports
`mutePublishedTrack(room, identity, trackSid, muted)`. Track SIDs are not known
client-side; the server must first call `getParticipant()` to resolve the SID
for a given source type.

## Goals / Non-Goals

**Goals:**

- Backend: two new host-only API endpoints (`muteAll`, `muteTrack`) following
  existing patterns
- Backend: extend `LiveKitPort` and `LiveKitAdapter` with track-mute operations
- Android: wire `muteAll` and per-participant `muteTrack` through repository,
  ViewModel, and UI
- Track-level mute only — no publish permission revocation
- State propagation to all clients via LiveKit SDK's native `TrackMuted` event
  (no SSE needed)

**Non-Goals:**

- Remote unmute: hosts can only mute, not unmute (privacy model matching
  Zoom/Google Meet)
- Web frontend: out of scope for this change
- Guest participant muting: guests cannot publish audio/video, so muting is not
  applicable
- Persistent mute state: mute is transient; rejoining clears it

## Decisions

### Track mute via `mutePublishedTrack`, not permission revocation

`mutePublishedTrack` silences an active track without revoking the participant's
publish permission. Revoking permissions would require the participant to
re-negotiate and would prevent them from unmuting themselves later. The chosen
approach matches industry privacy conventions: host controls the floor,
participant retains agency to unmute.

**Alternatives considered**: Permission revocation via `updateParticipant` —
rejected because it is destructive (participant loses the ability to unmute) and
inconsistent with the mute semantics users expect.

### Backend resolves trackSid server-side

The client does not send a trackSid. The backend calls
`RoomServiceClient.getParticipant()` to fetch the active participant info and
extracts the trackSid for the requested source (`microphone` or `camera`). This
keeps the API surface simple and avoids relying on client-provided identifiers
that could be stale.

**Alternatives considered**: Client sends trackSid in the request body —
rejected because it exposes internal LiveKit detail to the Android client,
creating unnecessary coupling and versioning risk.

### `muteAll` uses best-effort semantics

If one participant's mute fails (e.g., they left mid-operation), the operation
continues for remaining participants. The endpoint returns 204 if at least one
mute succeeds or the participant list is empty. This matches the
`MeetingSettingsChangedHandler` pattern.

**Alternatives considered**: All-or-nothing transaction — rejected because a
single recently-departed participant should not prevent muting everyone else.

### No SSE for mute state propagation

LiveKit's client SDK fires `TrackMuted`/`TrackUnmuted` events on all subscribers
automatically when `mutePublishedTrack` is called. The existing
`RoomEventListenerImpl` in `LiveKitRepositoryImpl` already handles these events
and calls `notifyParticipantsUpdated()`, which drives UI refresh. No additional
signaling layer is needed.

### New `ParticipantMuteListener` interface on the adapter

`ParticipantAdapter` gets a `ParticipantMuteListener` callback interface.
`ParticipantsBottomSheet` implements this interface and delegates to
`callViewModel`. This keeps the adapter free of ViewModel dependency and is
consistent with the Android idiom used in other adapters in the codebase.

### Host-only visibility in Android UI

`btnMuteAll` is shown only when `callViewModel.isHost()` is `true`.
Per-participant mute affordances in `ParticipantAdapter` are rendered only when
the `isHost` flag passed to the adapter is `true` and the target participant is
not local and not a host.

## Risks / Trade-offs

- **Race condition — participant leaves during muteAll**: `getParticipant()` may
  return 404 for a participant who left after the session list was loaded. This
  is handled by treating 404 as a non-fatal skip (best-effort).
- **TrackSid not found**: If a participant has not published a track of the
  requested source type (e.g., camera not started), `getParticipant()` returns
  an entry with no matching track. The use case returns
  `MeetingError.TrackNotFound`, and the controller maps this to HTTP 422.
- **Self-mute guard**: The `muteTrack` endpoint explicitly rejects requests
  where the target identity belongs to the host. Hosts must use their local
  device controls for their own media.
- **Android UI state lag**: The optimistic UI update is not used. The
  participant list refreshes only when the LiveKit `TrackMuted` event arrives.
  For `muteAll`, this means UI updates arrive asynchronously per-participant as
  events fire, which is the correct behavior.

## Migration Plan

1. Deploy backend changes first (new endpoints are additive, no schema changes)
2. Regenerate OpenAPI spec from Spring annotations
3. Regenerate Android API client from the new spec
4. Ship Android update with new UI controls

No database migrations required. No rollback complexity — new endpoints do
nothing until Android client ships.
