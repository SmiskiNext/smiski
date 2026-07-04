# Tasks

## 1. Backend room metadata publication

- [x] 1.1 Add `updateRoomMetadata(LiveKitRoomName, String metadata)` to the
      backend LiveKit port and implement it in the LiveKit adapter using the
      LiveKit server SDK.
- [x] 1.2 Update `StartRecordingUseCase` to publish `{"recording": true}` only
      after recording start succeeds.
- [x] 1.3 Update `FinalizeRecordingUseCase` to publish `{"recording": false}`
      for completed and failed recordings while leaving `StopRecordingUseCase`
      unchanged. ← (verify: metadata is set only from confirmed lifecycle
      transitions and stop requests do not clear it early)

## 2. Android recording data and meeting-state plumbing

- [x] 2.1 Create the Android `RecordingRepository` contract in
      `domain/repository` and implement `RecordingRepositoryImpl` with the
      generated `RecordingsApi` start/stop calls returning `CompletableFuture`.
- [x] 2.2 Register the new repository binding in `RepositoryModule` and add or
      update repository unit tests for success and error propagation.
- [x] 2.3 Extend the LiveKit room listener contract and
      `LiveKitRepositoryImpl.checkRoomStateChanges()` to emit room metadata
      changes when the metadata payload differs from the previous poll.
- [x] 2.4 Inject `RecordingRepository` into `CallViewModel` and add recording
      LiveData, metadata parsing, and `startRecording()`, `stopRecording()`, and
      `toggleRecording()` methods with separate loading and error handling. ←
      (verify: malformed metadata does not crash, loading state is independent
      from active recording state, and start/stop errors map to the expected UI
      state)

## 3. Android active-call UI and resources

- [x] 3.1 Update `fragment_active_call.xml` to add the host-only record control
      between More and End Call and add the top-bar recording indicator for all
      participants.
- [x] 3.2 Update `ActiveCallFragment` to bind the new views, observe recording
      state/error LiveData, prevent double taps during loading, and show
      Snackbar errors.
- [x] 3.3 Add the pulsing indicator animation behavior, record icon drawable,
      English strings, and Vietnamese translations for the new recording UX. ←
      (verify: hosts can start and stop recording from the control bar, guests
      never see the control, and all participants see the indicator react to
      metadata changes)

## 4. Validation

- [x] 4.1 Add or update `CallViewModel` unit tests covering start success, stop
      success, start failure, stop failure, and metadata-driven state changes.
- [x] 4.2 Run the relevant backend and Android test suites for the touched
      recording components.
- [ ] 4.3 Perform an end-to-end manual validation with a host and guest session
      to confirm metadata-driven recording indicator behavior across join, stop,
      and webhook finalization flows. ← (verify: host rejoin and guest join pick
      up ongoing recording immediately, and the indicator clears only after
      finalization metadata is published)

    **Note: requires manual testing in a live environment** — a running LiveKit
    server, the meeting-management backend, and physical or emulated Android
    devices are all needed. This task cannot be automated in a CI/unit-test
    context.
