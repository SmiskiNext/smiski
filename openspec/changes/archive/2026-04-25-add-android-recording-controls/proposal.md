# Why

Android users can already review completed recordings, but they cannot start,
stop, or observe live recording state during a meeting even though the backend
already supports the full recording lifecycle. Adding in-call recording controls
now closes that product gap by reusing existing backend endpoints and LiveKit
room metadata so hosts can manage recordings and all participants can see
recording status in real time.

## What Changes

- Add host-only in-call recording controls to the Android active call control
  bar, including idle, loading, and active recording states.
- Add a recording indicator for all Android meeting participants so ongoing
  recording is visible immediately after join and throughout the session.
- Add Android domain and data-layer support for starting and stopping recordings
  through the existing backend recording APIs.
- Extend Android call-state handling to observe LiveKit room metadata changes
  and map recording metadata into `CallViewModel` LiveData for UI updates.
- Add minimal backend support to publish recording state into LiveKit room
  metadata after recording starts and after recording finalizes.
- Add Android strings, icons, animations, and tests required to support the
  recording workflow and localized UX.

## Capabilities

### New Capabilities

- `android-recording-controls`: Host-controlled Android in-call recording
  actions and participant-visible live recording state during meetings.

### Modified Capabilities

- None.

## Impact

- Android app active-call UI, `CallViewModel`, LiveKit room-state polling,
  repository and Hilt wiring, localized resources, and unit tests.
- Backend recording application flow, LiveKit integration port/adapter, and room
  metadata updates after recording lifecycle transitions.
- Existing backend recording REST endpoints are reused; no public API contract
  changes are required.
- LiveKit room metadata becomes the cross-platform signaling mechanism for
  active recording state.
