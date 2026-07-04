# Tasks

## 1. Backend — Domain Layer

- [x] 1.1 Add `CanNotMuteSelf` error record to `MeetingError` sealed interface
- [x] 1.2 Add `TrackNotFound(String identity, String source)` error record to
      `MeetingError` sealed interface
- [x] 1.3 Add
      `muteParticipantTrack(LiveKitRoomName roomName, String identity, String source)`
      method to `LiveKitPort` interface
- [x] 1.4 Add
      `muteAllParticipantMicTracks(LiveKitRoomName roomName, List<String> identities)`
      method to `LiveKitPort` interface ← (verify: port interface compiles, all
      existing implementors still satisfy the contract)

## 2. Backend — Infrastructure Layer

- [x] 2.1 Implement `muteParticipantTrack` in `LiveKitAdapter`: call
      `roomServiceClient.getParticipant()` to resolve the track SID for the
      given source, then call `mutePublishedTrack()`; return `TrackNotFound` if
      no matching published track exists, `LiveKitUnavailable` on network error
- [x] 2.2 Implement `muteAllParticipantMicTracks` in `LiveKitAdapter`: iterate
      identities, call `muteParticipantTrack` for each, skip 404 responses
      (participant already left), return `LiveKitUnavailable` only if all calls
      fail ← (verify: blocking `call.execute()` used correctly on virtual
      threads, 404 treated as non-fatal skip, success returned when at least one
      mute succeeds)

## 3. Backend — Application Layer

- [x] 3.1 Create `MuteParticipantTrackCommand` record (`meetingId`,
      `requesterId`, `targetIdentity`, `source`)
- [x] 3.2 Create `MuteAllParticipantsCommand` record (`meetingId`,
      `requesterId`)
- [x] 3.3 Implement `MuteParticipantTrackUseCase`: load meeting, verify host,
      validate meeting is LIVE, reject self-mute (`CanNotMuteSelf`), call
      `liveKitPort.muteParticipantTrack()`; return `Result<Void, MeetingError>`
- [x] 3.4 Implement `MuteAllParticipantsUseCase`: load meeting, verify host,
      validate meeting is LIVE, load active sessions via
      `participationLogRepository.findActiveByMeetingId()`, filter to
      PARTICIPANT role only, collect LiveKit identities, call
      `liveKitPort.muteAllParticipantMicTracks()`; return
      `Result<Void, MeetingError>` ← (verify: HOST and GUEST sessions are
      excluded, empty participant list returns success, best-effort semantics
      preserved)

## 4. Backend — Presentation Layer

- [x] 4.1 Add `MuteAllParticipantsUseCase` and `MuteParticipantTrackUseCase`
      constructor injection to `ParticipantController`
- [x] 4.2 Add `POST /{version}/meetings/{id}/participants:muteAll` endpoint
      (version 1.0): extract host userId, delegate to
      `MuteAllParticipantsUseCase`, return 204 on success
- [x] 4.3 Add `POST /{version}/meetings/{id}/participants/{identity}:muteTrack`
      endpoint (version 1.0): extract host userId, read `source` query param
      (`microphone` or `camera`), delegate to `MuteParticipantTrackUseCase`,
      return 204 on success
- [x] 4.4 Add `CanNotMuteSelf` and `TrackNotFound` error-to-HTTP mappings in
      `BaseController` pattern-match switch (422 for both)
- [x] 4.5 Add Swagger/OpenAPI annotations (`@Operation`, `@Parameter`) to both
      new endpoints ← (verify: endpoints match spec — correct HTTP method, path,
      version, auth; error cases map to correct HTTP status codes; OpenAPI
      annotations are complete enough for spec generation)

## 5. Backend — OpenAPI Regeneration

- [x] 5.1 Run `./gradlew :services:meeting-management:generateOpenApiDocs` (or
      equivalent task) to regenerate
      `services/meeting-management/build/openapi/openapi.yaml`
- [x] 5.2 Verify the two new endpoint paths appear in the generated YAML with
      correct request/response schemas ← (verify: `participants:muteAll` and
      `participants/{identity}:muteTrack` paths present, `source` query param
      documented, 204/403/422/503 responses present)

## 6. Android — API Client Regeneration

- [x] 6.1 Run the OpenAPI Generator task to regenerate `ParticipantsApi` from
      the updated `openapi.yaml`
- [x] 6.2 Verify `muteAll(meetingId)` and
      `muteTrack(meetingId, identity, source)` methods are present in the
      generated `ParticipantsApi` ← (verify: generated client compiles, method
      signatures match the backend spec)

## 7. Android — Repository Layer

- [x] 7.1 Add `muteAll(String meetingId)` returning `CompletableFuture<Void>` to
      `ParticipantRepository` domain interface
- [x] 7.2 Add `muteTrack(String meetingId, String identity, String source)`
      returning `CompletableFuture<Void>` to `ParticipantRepository` domain
      interface
- [x] 7.3 Implement `muteAll` in `ParticipantRepositoryImpl`: call generated
      `participantsApi.muteAll(meetingId)`, adapt to `CompletableFuture<Void>`
- [x] 7.4 Implement `muteTrack` in `ParticipantRepositoryImpl`: call generated
      `participantsApi.muteTrack(meetingId, identity, source)`, adapt to
      `CompletableFuture<Void>` ← (verify: repository impl compiles with new
      generated API, error propagation through CompletableFuture chain is
      consistent with existing methods)

## 8. Android — ViewModel

- [x] 8.1 Inject `ParticipantRepository` into `CallViewModel` constructor (if
      not already injected) and add Hilt binding if needed
- [x] 8.2 Implement `muteAllParticipants()` in `CallViewModel`: get `meetingId`
      from `_meetingId` LiveData, call
      `participantRepository.muteAll(meetingId)` on a background executor, log
      errors to `_settingsError` or a dedicated error LiveData
- [x] 8.3 Add `muteParticipantTrack(String identity, String source)` method to
      `CallViewModel`: get `meetingId`, call
      `participantRepository.muteTrack(meetingId, identity, source)` on
      background executor, log errors ← (verify: both methods execute off the
      main thread, errors surface without crashing; `muteAllParticipants` stub
      is fully replaced)

## 9. Android — Adapter and Listener Interface

- [x] 9.1 Create `ParticipantMuteListener` interface with methods
      `onMuteMic(String identity)` and `onMuteCamera(String identity)`
- [x] 9.2 Update `ParticipantAdapter` constructor to accept `boolean isHost` and
      `@Nullable ParticipantMuteListener muteListener`
- [x] 9.3 In `onBindViewHolder`, when `isHost` is `true` and the participant is
      not local and not HOST role: set `OnClickListener` on `btnMic` calling
      `muteListener.onMuteMic(identity)` and on `btnCamera` calling
      `muteListener.onMuteCamera(identity)`
- [x] 9.4 When `isHost` is `false` or participant is local or HOST: clear click
      listeners from `btnMic` and `btnCamera` (set to null) ← (verify: adapter
      renders correctly for all combinations — host/non-host viewer,
      local/remote participant, host/participant/guest role; no listener leaks
      across recycled views)

## 10. Android — UI (ParticipantsBottomSheet)

- [x] 10.1 Observe `callViewModel.isHost()` in
      `ParticipantsBottomSheet.setupObservers()`; when host, set `btnMuteAll` to
      `View.VISIBLE` and enabled; when not host, keep `View.GONE`
- [x] 10.2 Set `btnMuteAll` click listener to call
      `callViewModel.muteAllParticipants()`
- [x] 10.3 Implement `ParticipantMuteListener` in `ParticipantsBottomSheet`:
      `onMuteMic` calls
      `callViewModel.muteParticipantTrack(identity, "microphone")`;
      `onMuteCamera` calls
      `callViewModel.muteParticipantTrack(identity, "camera")`
- [x] 10.4 Pass `isHost` flag and `this` as `muteListener` when constructing
      `ParticipantAdapter` (update `setupRecyclerView` and any `adapter`
      recreation sites) ← (verify: btnMuteAll visible only when user is host;
      per-participant mute taps trigger correct ViewModel calls; no crash when
      host status changes during sheet lifecycle)

## 11. Verification

- [ ] 11.1 Backend integration test: POST `muteAll` as host on a LIVE meeting
      with active PARTICIPANT sessions — verify 204 and LiveKit adapter called
      per participant
- [ ] 11.2 Backend integration test: POST `muteAll` as non-host — verify 403
- [ ] 11.3 Backend integration test: POST `muteTrack` with valid
      `source=microphone` — verify 204 and correct trackSid resolution
- [ ] 11.4 Backend integration test: POST `muteTrack` where host targets
      themselves — verify 422 with `CANNOT_MUTE_SELF`
- [ ] 11.5 Backend integration test: POST `muteTrack` where participant has no
      camera track — verify 422 with `TRACK_NOT_FOUND`
- [ ] 11.6 Android manual test: open participants sheet as host — verify
      `btnMuteAll` is visible; as guest — verify hidden
- [ ] 11.7 Android manual test: tap `btnMuteAll` — verify API call reaches
      backend and remote participant mic state updates in UI ← (verify:
      end-to-end flow from button tap through ViewModel to repository to backend
      to LiveKit; TrackMuted event received by Android SDK updates participant
      list without manual refresh)
