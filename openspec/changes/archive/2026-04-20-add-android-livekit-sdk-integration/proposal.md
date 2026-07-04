# Why

The Android app currently stops at a placeholder video-call shell, so users can
enter the call flow but cannot request room access from the backend or connect
to a real LiveKit session. This change is needed now to turn the Android call
experience into a functional real-time meeting flow that matches the existing
backend join-approval model.

## What Changes

- Add LiveKit Android SDK v2.24.1 and Gradle repository configuration required
  to build the Android app with WebRTC-backed room connectivity
- Introduce Android Clean Architecture contracts and implementations for backend
  room join requests, SSE approval updates, and LiveKit room lifecycle
- Upgrade `CallViewModel`, `PreJoinFragment`, and `ActiveCallFragment` from
  placeholder state management to real connection, participant, and media-toggle
  behavior
- Redesign the active-call UI around a RecyclerView-based video grid,
  self-preview overlay, connection quality display, and modernized floating
  controls
- Add supporting call-theme colors, drawable resources, and any required
  ProGuard guidance for LiveKit/WebRTC packaging

## Capabilities

### New Capabilities

- `android-livekit-room-join`: Define the Android client flow for requesting
  room access, reacting to approval SSE events, and connecting to a LiveKit room

### Modified Capabilities

- `android-videocall-shell`: Replace placeholder pre-join and active-call
  behavior with backend-driven join flow, LiveKit-backed room state, and dynamic
  in-call video surfaces
- `android-i18n-main`: Extend Android call-surface string and accessibility
  requirements for the new join states, controls, connection indicators, and
  participant video UI

## Impact

**Code Changes:**

- `frontends/android-app/settings.gradle.kts` - add JitPack for LiveKit
- `frontends/android-app/app/build.gradle.kts` - add LiveKit SDK and SSE-related
  dependencies, plus any packaging/ProGuard updates
- `frontends/android-app/app/src/main/java/.../domain/repository/` - add
  `LiveKitRepository` and `JoinRoomRepository`
- `frontends/android-app/app/src/main/java/.../data/repository/` - implement
  LiveKit room connection/disconnection and backend join/SSE flows
- `frontends/android-app/app/src/main/java/.../presentation/videocall/` - update
  `CallViewModel`, `PreJoinFragment`, `ActiveCallFragment`, and add video-grid
  adapter/supporting UI logic
- `frontends/android-app/app/src/main/res/layout/` - redesign
  `fragment_active_call.xml`, add `item_video_tile.xml`, and update pre-join UI
  affordances where needed
- `frontends/android-app/app/src/main/res/drawable/` and `res/values/` - add
  call-specific icons, scrims, button backgrounds, and video-call colors/text

**APIs Used:**

- `POST /api/v1.0/meetings/{id}:requestJoin`
- `GET /api/v1.0/joinRequests/{requestId}/events`
- LiveKit room connection using server URL from `BuildConfig`

**Systems Affected:**

- Android video-call flow (`VideoCallActivity`, PreJoin, ActiveCall)
- Android DI, repository, and networking layers
- Meeting join approval flow between Android client, backend, and LiveKit
