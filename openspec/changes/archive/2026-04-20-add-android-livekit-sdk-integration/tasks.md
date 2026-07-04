# Tasks

## 1. Build and dependency setup

- [x] 1.1 Add JitPack to `frontends/android-app/settings.gradle.kts`
- [x] 1.2 Add LiveKit Android SDK v2.24.1 and any SSE support dependency to
      `frontends/android-app/app/build.gradle.kts`
- [x] 1.3 Add or document release shrinker rules required for LiveKit/WebRTC
      integration ← (verify: debug and release dependency configuration both
      resolve, and release rules do not strip LiveKit room connection/rendering
      classes)

## 2. Domain contracts and models

- [x] 2.1 Create `LiveKitRepository` interface for room connection, disconnect,
      and local media controls
- [x] 2.2 Create `JoinRoomRepository` interface for join request submission and
      pending approval subscription lifecycle
- [x] 2.3 Add domain models/enums needed for join response state, SSE outcome,
      and room connection state
- [x] 2.4 Define any callback/listener contracts needed for propagating LiveKit
      room and participant events into presentation ← (verify: domain contracts
      cleanly separate backend approval flow from media-room control and cover
      all required LiveKit/backend event cases)

## 3. Data-layer join request and SSE implementation

- [x] 3.1 Implement `JoinRoomRepositoryImpl` using the generated backend API for
      `POST /api/v1.0/meetings/{id}:requestJoin`
- [x] 3.2 Build request payload handling for `displayName` and `deviceId`, with
      approved and pending response mapping
- [x] 3.3 Implement SSE subscription support for
      `GET /api/v1.0/joinRequests/{requestId}/events` using OkHttp EventSource
      and `Handler`
- [x] 3.4 Map `join_request_approved`, `join_request_denied`, and
      `join_request_expired` events into app-level outcomes and ensure
      subscriptions are cancelled on terminal states ← (verify: pending approval
      flows do not leak listeners, and all terminal SSE outcomes map to the
      correct app state)

## 4. LiveKit repository and DI wiring

- [x] 4.1 Implement `LiveKitRepositoryImpl` with `LiveKit.create()` and
      `room.connect()` using the server URL from `BuildConfig`
- [x] 4.2 Add event handling for Connected, Disconnected, FailedToConnect,
      Reconnecting, Reconnected, ParticipantConnected, ParticipantDisconnected,
      TrackSubscribed, TrackUnsubscribed, and ActiveSpeakersChanged
- [x] 4.3 Implement disconnect plus local microphone/camera control through
      `setMicrophoneEnabled(...)` and `setCameraEnabled(...)`
- [x] 4.4 Bind the new repositories in Hilt modules and provide any auxiliary
      objects needed for LiveKit/SSE construction ← (verify: DI resolves
      cleanly, room lifecycle methods work through the repository abstraction,
      and all required LiveKit events reach the app layer)

## 5. CallViewModel connection orchestration

- [x] 5.1 Inject `JoinRoomRepository`, `LiveKitRepository`, and any needed
      executors/dependencies into `CallViewModel`
- [x] 5.2 Add LiveData for connection state, participants, local video track,
      remote video tracks, and join/waiting/error UI events
- [x] 5.3 Implement join-request orchestration that handles APPROVED and PENDING
      backend responses and triggers room connection at the correct time
- [x] 5.4 Implement `connectToRoom(url, token)`, `toggleLocalMic()`,
      `toggleLocalCamera()`, and `endCall()` against the LiveKit repository
- [x] 5.5 Preserve timer behavior and ensure cleanup on disconnect and
      `onCleared()` ← (verify: ViewModel state survives fragment transitions,
      media toggles stay in sync with LiveData, and disconnect/cleanup happens
      on both manual end-call and teardown paths)

## 6. Pre-join flow integration

- [x] 6.1 Update `PreJoinFragment` to call the backend join flow instead of
      navigating immediately after permission checks
- [x] 6.2 Handle approved responses by persisting AV preferences and navigating
      to `ActiveCallFragment` with the token stored in shared ViewModel state
- [x] 6.3 Handle pending responses by showing a waiting dialog and subscribing
      to SSE approval updates
- [x] 6.4 Handle denied and expired outcomes with user-facing errors and reset
      the pre-join UI to a retryable state ← (verify: pre-join validation,
      permission checks, approval waiting, and navigation timing all match the
      join-room contract)

## 7. Active call UI and rendering

- [x] 7.1 Redesign `fragment_active_call.xml` around a RecyclerView grid,
      self-view overlay, top-bar quality indicator, and MaterialCardView control
      bar
- [x] 7.2 Create `item_video_tile.xml` with renderer container, participant name
      overlay, mic-muted badge, active-speaker border, and camera-off
      placeholder
- [x] 7.3 Implement `VideoGridAdapter` and view-holder logic for
      `SurfaceViewRenderer` attach/detach behavior
- [x] 7.4 Add dynamic `GridLayoutManager` span-count rules based on participant
      count and update `ActiveCallFragment` to observe participant/track state
- [x] 7.5 Implement self-view drag behavior, control auto-hide after 3 seconds,
      and tap-to-show interactions ← (verify: video tiles recycle safely without
      stale surfaces, active-speaker and mute/camera-off states render
      correctly, and controls behave as specified)

## 8. Resources, theme, and accessibility updates

- [x] 8.1 Add video-call color resources for dark background, surfaces, and text
      treatment
- [x] 8.2 Add the required drawable resources for call controls, connection
      quality, scrims, borders, and button states
- [x] 8.3 Add or update string resources for waiting-room states, connection
      quality, tile labels, control descriptions, and errors in
      `values/strings.xml`
- [x] 8.4 Add matching Vietnamese translations in `values-vi/strings.xml` and
      wire content descriptions/accessibility labels throughout the call UI ←
      (verify: no new user-facing call text is hardcoded, Vietnamese parity is
      maintained, and accessibility labels cover the new call controls and tile
      statuses)

## 9. Validation and regression checks

- [x] 9.1 Run Android compilation/build validation for the updated app module
      and fix any dependency or API integration issues
- [ ] 9.2 Manually test pre-join approval scenarios: immediate approval, pending
      approval then approve, denied, and expired
- [ ] 9.3 Manually test active-call behaviors: connect, reconnect, participant
      join/leave, mic/camera toggles, and end call
- [ ] 9.4 Verify visual behavior in dark mode, dynamic participant layouts,
      connection quality indicators, and release-build shrinker compatibility ←
      (verify: end-to-end Android LiveKit join flow, UI state transitions, and
      packaging behavior all match proposal, design, and spec requirements)
