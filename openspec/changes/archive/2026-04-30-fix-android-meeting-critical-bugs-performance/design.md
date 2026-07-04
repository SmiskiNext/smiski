# Context

This change targets high-impact reliability and performance defects in the
Android in-call experience under `frontends/android-app/`, specifically within
participant management, waiting-room moderation, and local media control
behavior. The current implementation contains several user-visible mismatches
between UI and actual behavior (for example, mute-all fake success and
camera-switch no-op), plus asynchronous ordering issues between LiveKit
connection, SSE updates, and API synchronization.

The app uses Java + XML, MVVM with `LiveData`, Hilt DI, repository abstractions,
and RecyclerView-based adapters. The change must preserve that architecture,
avoid backend contract changes, and avoid introducing new dependencies.

## Goals / Non-Goals

**Goals:**

- Eliminate misleading in-call controls by ensuring UI only exposes actions that
  actually work.
- Make local media initialization deterministic by applying desired mic/camera
  states only after successful room connection.
- Remove race conditions in host waiting-room pending-request state
  synchronization between SSE and API sync paths.
- Enforce stable participant identity matching for role enrichment to prevent
  duplicate-name collisions.
- Reduce UI jank and unnecessary binding work by replacing broad adapter
  refreshes with DiffUtil/targeted updates.
- Harden repository error handling for invalid UUID inputs to prevent uncaught
  runtime exceptions.

**Non-Goals:**

- No backend API additions or contract modifications.
- No migration from Java/LiveData/RecyclerView to Kotlin, StateFlow, Compose, or
  other UI stack changes.
- No introduction of new third-party dependencies.
- No broad redesign of meeting UX beyond the specified bug fixes and performance
  optimizations.

## Decisions

### 1) Gate unsupported controls at the UI layer

- Decision: Disable or hide mute-all action and remove success Snackbar while
  backend support is absent.
- Rationale: A no-op action with success feedback is more damaging than
  temporary unavailability because it creates false host confidence.
- Alternative considered: Keep action visible with an error Snackbar (“not
  supported yet”). Rejected because repeated actionable-looking failure degrades
  UX and can be interpreted as transient error rather than unsupported feature.

### 2) Move initial mic/camera application into post-connect repository flow

- Decision: Extend connection flow so desired initial mic/camera states are
  applied only after LiveKit room connection is confirmed; ViewModel passes
  desired state into repository connect call.
- Rationale: Eliminates race where toggles are issued before room/local
  participant availability.
- Alternative considered: Retry local media toggles from ViewModel with
  delays/polling. Rejected due to brittle timing assumptions and duplicated
  connection-state logic.

### 3) Implement camera switching in repository using active local video track

- Decision: Implement `switchCamera()` in `LiveKitRepositoryImpl` against
  existing local camera track lifecycle, toggling front/back position via
  LiveKit SDK camera-position API.
- Rationale: Repository already owns LiveKit integration and should remain
  single source of media-control behavior.
- Alternative considered: Trigger camera switch directly from UI layer. Rejected
  to preserve MVVM boundaries and keep SDK coupling out of fragments.

### 4) Make waiting-room sync additive against SSE state

- Decision: Change pending request sync semantics from replace to
  merge-by-requestId, preserving newer locally observed SSE entries.
- Rationale: Prevents stale API responses from overwriting a more recent local
  state timeline.
- Alternative considered: Sequence enforcement with strict lock/serialization.
  Rejected as unnecessarily complex for current state model; merge-by-key solves
  correctness with minimal change.

### 5) Treat SSE connected state as transport truth, not attempt state

- Decision: Set `_isWaitingRoomSseConnected` only in SSE `onConnected` callback.
- Rationale: Avoids false-positive connection indicators and incorrect UI
  assumptions during failed connection attempts.
- Alternative considered: Keep optimistic `true` and compensate on failure
  callback. Rejected because intermediate UI state remains incorrect and can
  trigger wrong decisions.

### 6) Normalize participant identity matching to stable IDs only

- Decision: Use LiveKit participant `identity` as participant ID and resolve
  roles by ID-only matching; remove display-name fallback path.
- Rationale: Display names are non-unique and mutable; ID-only matching avoids
  collisions and wrong role attribution.
- Alternative considered: Display-name fallback only when IDs missing. Rejected
  because this produces silent role corruption in duplicate-name meetings.

### 7) Optimize list/grid updates with DiffUtil and targeted notifications

- Decision: Convert participant/join-request adapters to `ListAdapter` with
  `DiffUtil.ItemCallback`; for active speaker changes, notify only changed
  positions.
- Rationale: Reduces full list rebinds and avoids frame drops on frequent
  speaker/presence updates.
- Alternative considered: Continue `notifyDataSetChanged()` for simplicity.
  Rejected due to avoidable performance costs in real-time call UIs.

### 8) Avoid duplicate participant-list emissions for active speaker changes

- Decision: Remove participant-list update emission from active-speaker update
  path and rely on dedicated active-speaker callback/state.
- Rationale: Active speaker highlight does not require rebuilding participant
  list model and currently causes double UI work.
- Alternative considered: Keep both emissions and debounce in UI. Rejected
  because root cause belongs in repository event mapping.

### 9) Catch UUID parsing failures with existing repository error style

- Decision: Add `IllegalArgumentException` handling alongside `IOException` in
  waiting-room repository methods that parse UUIDs.
- Rationale: Prevents crash paths from malformed IDs while maintaining
  established failure-return patterns.
- Alternative considered: Pre-validate UUID format at all call sites. Rejected
  as error-prone duplication; repository boundary is correct defensive layer.

## Risks / Trade-offs

- [Risk] Camera-switch API behavior may vary with current track state/device
  camera availability. → Mitigation: use existing local video track creation
  path, no-op safely when no local camera track exists, and log warnings for
  diagnosis.
- [Risk] Merge-by-ID for waiting-room sync may retain obsolete entries if
  expiration events are delayed/lost. → Mitigation: continue processing
  expiration/deny/approve removal events and run periodic/triggered sync after
  reconnect.
- [Risk] Removing display-name role fallback may leave some participants as
  default role if upstream identity is missing. → Mitigation: explicitly default
  to `PARTICIPANT` and keep participant visible; prefer correctness over
  incorrect privileged role assignment.
- [Risk] DiffUtil conversion can surface subtle equality/immutability issues in
  adapter inputs. → Mitigation: define explicit item/content equality fields
  (id, name, role, mic/cam states) and submit immutable list snapshots.

## Migration Plan

1. Implement repository and ViewModel fixes for connection sequencing, camera
   switching, active-speaker emission behavior, and UUID handling.
2. Apply waiting-room synchronization and SSE connection-state corrections.
3. Convert adapters to DiffUtil/ListAdapter and targeted active-speaker update
   notifications.
4. Validate host/non-host call flows manually in debug builds (no contract
   changes required).
5. Rollback strategy: revert Android app-only commits for this change; no
   backend/data migration rollback needed.

## Open Questions

- Confirm exact LiveKit SDK method signature available in current pinned version
  for camera position switching to align implementation with existing track
  abstraction.
- Decide final mute-all UX treatment between hidden vs disabled-with-tooltip
  based on existing design language in `ParticipantsBottomSheet`.
