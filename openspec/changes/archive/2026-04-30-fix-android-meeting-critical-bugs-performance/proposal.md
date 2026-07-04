# Why

The Android meeting experience currently has critical correctness gaps (no-op
controls that appear successful, race conditions in connection and waiting-room
synchronization, and identity mismatches) that can mislead users and break host
moderation flows. Addressing these defects now is necessary to restore trust and
stabilize core in-call behavior before further feature expansion.

## What Changes

- Remove misleading mute-all UX behavior by disabling/hiding the action until
  backend support exists and eliminating fake success feedback.
- Implement real camera switching in the LiveKit repository and align local
  media state application with successful room connection timing.
- Fix waiting-room synchronization races by merging API sync results with
  SSE-driven local state and updating SSE connected-state signaling only after
  confirmed connection.
- Correct participant identity/role resolution by using stable LiveKit identity
  as participant ID and removing display-name fallback role matching.
- Improve list and grid rendering performance by replacing broad
  `notifyDataSetChanged()` updates with DiffUtil/ListAdapter and targeted
  per-item notifications for active-speaker changes.
- Ensure waiting-room sheet content auto-refreshes from ViewModel LiveData
  updates and harden UUID parsing error handling in waiting-room repository
  methods.

## Capabilities

### New Capabilities

- `android-videocall-performance-stability`: Defines Android in-call correctness
  and performance guarantees for media controls, participant rendering updates,
  and waiting-room state consistency under real-time events.

### Modified Capabilities

- `android-livekit-room-join`: Refine requirements for post-connect local media
  state application and functional camera-switch behavior.
- `android-host-waiting-room`: Refine requirements for SSE connection-state
  truthfulness, stale-safe pending-request synchronization, and live sheet
  refresh behavior.
- `android-real-participant-list`: Refine participant identity and
  role-resolution requirements to require stable ID-only matching and avoid
  display-name collisions.

## Impact

- Affected Android app modules in
  `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/`:
    - `presentation/videocall` (CallViewModel, bottom sheets, adapters)
    - `presentation/meeting/participant` (ParticipantsViewModel,
      ParticipantAdapter)
    - `data/repository` (LiveKitRepositoryImpl, WaitingRoomRepositoryImpl,
      ParticipantRepositoryImpl)
- No backend API contract changes; existing endpoints and SSE event types are
  reused.
- No new dependencies expected; implementation should rely on existing AndroidX
  RecyclerView DiffUtil/ListAdapter and current LiveKit SDK APIs.
