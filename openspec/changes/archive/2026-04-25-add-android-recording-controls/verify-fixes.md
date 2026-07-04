## [2026-04-25] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Added metadata-driven state transition tests to `CallViewModelTest` —
  captures the `RoomEventListener` via `ArgumentCaptor` during `@Before` setup,
  then exercises `onRoomMetadataChanged` with `{"recording":true}`,
  `{"recording":false}`, malformed JSON, empty string, and null to assert
  correct `isRecording` and `isRecordingLoading` LiveData values. Also added
  `toggleRecording_whenAlreadyRecording_callsStop` covering the stop branch of
  `toggleRecording()`.
- Fixed: Created `RecordingRepositoryImplTest` at
  `frontends/android-app/app/src/test/java/io/github/phunguy65/zms/data/repository/RecordingRepositoryImplTest.java`
  covering start success, stop success, start HTTP 409 error propagation, stop
  HTTP 500 error propagation, start with invalid UUID, and stop with invalid
  UUID — all following existing repository test style with `MockitoJUnitRunner`
  and `immediateExecutor`.
- Fixed: Added
  `verify(liveKitPort).updateRoomMetadata(..., "{\"recording\":true}")`
  assertion to `startRecording_startsEgressAndPersistsPendingRecording` in
  `RecordingFlowUseCaseTest` and added new
  `stopRecording_neverCallsUpdateRoomMetadata` test asserting
  `verify(liveKitPort, never()).updateRoomMetadata(any(), any())` in the stop
  path.
- Fixed: Added
  `verify(liveKitPort).updateRoomMetadata(..., "{\"recording\":false}")`
  assertions to `finalizeRecording_completesPendingRecordingFromWebhook`,
  `finalizeRecording_marksRecordingFailedWhenWebhookContainsError`, and
  `finalizeRecording_completesAlreadyRecordingSession` in
  `RecordingWebhookUseCaseTest`.
- Confirmed: Task 4.3 remains unchecked in `tasks.md` with existing note that it
  requires a live environment and cannot be automated in CI/unit-test context.
