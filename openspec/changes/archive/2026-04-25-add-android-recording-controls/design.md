# Context

The monorepo already supports the recording lifecycle on the backend and
playback history on Android, but the live meeting experience still lacks any way
to control or observe recording state in real time. This change spans the Spring
Boot backend, LiveKit integration, Android data/domain/presentation layers, and
shared meeting state propagation, so documenting the technical contract before
implementation reduces integration drift.

Android follows MVVM with LiveData, Hilt, and CompletableFuture-based
repositories. The in-call experience is centered on `CallViewModel`,
`ActiveCallFragment`, and `LiveKitRepositoryImpl`, which already polls room
state every 500ms. The backend already starts and finalizes recordings through
LiveKit egress and webhook-driven state transitions, so the missing piece is
broadcasting recording state through room metadata and consuming that signal in
Android.

## Goals / Non-Goals

**Goals:**

- Add host-only Android controls to start and stop meeting recording using the
  existing recording REST API.
- Expose live recording state to all Android participants, including newly
  joined guests, through a participant-visible indicator.
- Use LiveKit room metadata as the single real-time recording-state signal so
  Android can react without adding new SSE event types.
- Keep backend changes minimal and aligned with existing Clean Architecture
  boundaries by extending the LiveKit port/adapter and existing recording use
  cases.
- Preserve current Android architecture patterns: repository contracts in
  `domain/`, Retrofit-backed implementations in `data/`, and LiveData-driven UI
  state in `CallViewModel`.
- Cover the new repository and ViewModel behavior with unit tests following
  existing project patterns.

**Non-Goals:**

- Changing the existing public recording REST endpoints, response schemas, or
  recording domain lifecycle.
- Replacing Android polling with a push-based metadata subscription mechanism.
- Adding new recording management UI outside the active call surface.
- Introducing recording download, deletion, or playback enhancements beyond the
  already supported history flow.
- Adding new backend SSE event categories for recording state.

## Decisions

### Use LiveKit room metadata as the authoritative active-recording signal

The backend will write room metadata as JSON with a `recording` boolean after
recording starts successfully and after recording is finalized as completed or
failed. Android will read the room metadata from the existing
`LiveKitRepositoryImpl.checkRoomStateChanges()` polling loop and forward changes
through a new `onRoomMetadataChanged(String metadata)` callback.

Rationale:

- It works for hosts, authenticated members, and guests without requiring
  app-specific event channels.
- It survives participant reconnects because the metadata lives with the room,
  not with a transient client session.
- It reuses an existing polling mechanism instead of creating another real-time
  transport.

Alternatives considered:

- SSE-based recording events: rejected because guests may not have the same
  authenticated SSE path and it adds another backend event surface to maintain.
- Android-only optimistic state after start/stop calls: rejected because it
  would desynchronize when webhook finalization changes the backend state or
  when a participant rejoins mid-recording.

### Publish metadata only from confirmed lifecycle transitions

`StartRecordingUseCase` will update room metadata only after LiveKit egress
start succeeds. `FinalizeRecordingUseCase` will clear metadata after the
recording reaches a terminal state from webhook processing.
`StopRecordingUseCase` will not clear metadata directly.

Rationale:

- The room should only advertise recording when the backend has confirmed an
  active egress session.
- Clearing metadata in finalize keeps the indicator tied to the actual terminal
  transition rather than the user’s stop request, which may precede webhook
  completion.
- This matches the existing backend lifecycle where stop requests initiate
  shutdown but final state is established asynchronously.

Alternatives considered:

- Clearing metadata immediately in `StopRecordingUseCase`: rejected because the
  UI could show recording as stopped before LiveKit finalization succeeds.
- Updating metadata from the controller layer: rejected because lifecycle
  ownership belongs in the use cases and port boundary.

### Add a dedicated Android recording repository contract instead of calling generated APIs from the ViewModel

Android will introduce `RecordingRepository` in `domain/repository` and
`RecordingRepositoryImpl` in `data/repository`, backed by the generated
`RecordingsApi`. `CallViewModel` will depend only on the repository contract and
expose `startRecording()`, `stopRecording()`, and `toggleRecording()`.

Rationale:

- It preserves the existing Clean Architecture boundaries from the codemap.
- It keeps Retrofit- and generated-model concerns out of presentation code.
- It makes unit testing straightforward by mocking the repository contract.

Alternatives considered:

- Injecting `RecordingsApi` directly into `CallViewModel`: rejected because it
  violates the app’s architectural dependency direction.
- Folding recording actions into `MeetingRepository`: rejected because recording
  lifecycle and meeting session lifecycle are separate concerns with different
  API surfaces.

### Model recording UI as two orthogonal pieces of state

`CallViewModel` will keep `_isRecording` for the room-wide active state and
`_isRecordingLoading` for transient host action state. The fragment will derive
button presentation from both values: idle, starting, recording, and stopping.
Errors will be exposed via `_recordingError` and consumed as one-shot UI
messages by the fragment.

Rationale:

- Room-wide state and local action progress are not equivalent; all participants
  need active-state visibility, but only the host needs loading feedback.
- Separating them avoids incorrect transitions such as clearing the active
  indicator during a stop request before finalization metadata arrives.
- It supports retry flows where stop fails but the room is still actively
  recording.

Alternatives considered:

- Single enum state for both shared and local behavior: rejected because guest
  UI and host UI have different needs and the combined state would be harder to
  reconcile with asynchronous metadata updates.

### Keep the active-call UI changes additive to the current layout pattern

The primary control bar will add a dedicated record control between More and End
Call for hosts only. A top-bar indicator with a pulsing red dot and `REC` label
will be visible for all participants whenever `_isRecording` is true. The pulse
animation will use standard Android property animation (`ObjectAnimator`) so it
remains lightweight and self-contained.

Rationale:

- The control bar requirement is explicit and keeps recording as a first-class
  host action.
- The top-bar indicator separates global awareness from host-only controls.
- Reusing standard animation APIs avoids introducing new dependencies.

Alternatives considered:

- Putting recording only in the bottom sheet: rejected because the requirement
  calls for a dedicated primary control.
- Snackbar-only notification without persistent indicator: rejected because
  participants need continuous awareness while recording remains active.

## Risks / Trade-offs

- Metadata parse failures on Android → Mitigation: treat malformed or empty
  metadata as `recording=false`, log the failure path, and avoid crashing the
  call UI.
- Polling every 500ms may delay indicator updates slightly → Mitigation: reuse
  the established polling cadence and keep the metadata payload minimal so no
  extra polling channel is introduced.
- Start/stop requests and webhook-driven finalize events can arrive at different
  times → Mitigation: keep `_isRecordingLoading` separate from `_isRecording`
  and let finalize metadata be the source of truth for clearing the active
  state.
- Backend metadata updates could fail after recording transitions succeed →
  Mitigation: surface failures through existing backend logging/monitoring and
  ensure recording lifecycle completion does not depend on metadata write
  success.
- Host-only control visibility based on `isHost` can drift if host state changes
  late → Mitigation: derive button visibility from the same LiveData already
  used for other host controls and refresh UI through existing observers.

## Migration Plan

1. Extend the backend LiveKit port and adapter to support room metadata updates.
2. Update recording use cases so metadata is written after successful start and
   after finalization to terminal states.
3. Add Android repository wiring, ViewModel state, and LiveKit room metadata
   callbacks.
4. Update `fragment_active_call.xml` and `ActiveCallFragment` to render host
   controls, participant indicator, animations, and Snackbar error handling.
5. Add string/icon resources and localized Vietnamese translations.
6. Run backend and Android unit tests, then validate end-to-end behavior in a
   host-plus-guest meeting.

Rollback strategy:

- Backend rollback can remove metadata writes without affecting the existing
  recording API lifecycle.
- Android rollback can hide the new control and indicator while keeping the
  playback history feature intact.

## Open Questions

- None. The API endpoints, room metadata transport, polling approach, and
  lifecycle ownership are already decided in the request context.
