# ADDED Requirements

## Requirement: Android room join request integration

The Android app SHALL request room access from the backend before attempting a
LiveKit connection.

### Scenario: Approved join request connects immediately

- **WHEN** the user submits a valid meeting code and pre-join form in
  `PreJoinFragment`
- **THEN** the system SHALL call `POST /api/v1.0/meetings/{id}:requestJoin`
- **THEN** the request SHALL include `displayName` and `deviceId`
- **THEN** if the response status is `APPROVED`, the system SHALL extract
  `token` and `roomName` and proceed directly to LiveKit room connection

### Scenario: Pending join request waits for approval events

- **WHEN** `POST /api/v1.0/meetings/{id}:requestJoin` returns status `PENDING`
- **THEN** the system SHALL extract the returned `requestId`
- **THEN** the system SHALL subscribe to
  `GET /api/v1.0/joinRequests/{requestId}/events`
- **THEN** the pre-join UI SHALL remain in a waiting state until an approval,
  denial, expiration, or cancellation outcome is received

## Requirement: Android join approval SSE handling

The Android app SHALL react to backend join-request event streams for pending
meeting entry.

### Scenario: Approval event promotes pending user into room connection

- **WHEN** the SSE stream receives `join_request_approved`
- **THEN** the system SHALL extract the approved LiveKit token from the event
- **THEN** the system SHALL stop the waiting subscription
- **THEN** the system SHALL continue into ActiveCall navigation and room
  connection using the approved token

### Scenario: Denial event returns user to pre-join state

- **WHEN** the SSE stream receives `join_request_denied`
- **THEN** the system SHALL stop the waiting subscription
- **THEN** the system SHALL dismiss any waiting dialog or loading state
- **THEN** the system SHALL show an error message and keep or return the user to
  `PreJoinFragment`

### Scenario: Expiration event invalidates pending request

- **WHEN** the SSE stream receives `join_request_expired`
- **THEN** the system SHALL stop the waiting subscription
- **THEN** the system SHALL dismiss any waiting dialog or loading state
- **THEN** the system SHALL inform the user that the join request expired and
  require a new join attempt

## Requirement: Android LiveKit room repository

The Android app SHALL manage LiveKit room lifecycle through a dedicated
repository abstraction.

### Scenario: Room repository creates and connects LiveKit room

- **WHEN** `CallViewModel` requests a room connection with URL and token
- **THEN** the `LiveKitRepository` implementation SHALL create a room using
  `LiveKit.create()`
- **THEN** it SHALL call `room.connect()` with the configured server URL and
  token
- **THEN** the implementation SHALL use the LiveKit server URL from
  `BuildConfig`

### Scenario: Room repository supports disconnect and local AV controls

- **WHEN** the user ends a call or toggles local media
- **THEN** `LiveKitRepository` SHALL expose operations for disconnect,
  microphone enable/disable, and camera enable/disable
- **THEN** local media toggles SHALL call LiveKit's `setMicrophoneEnabled(...)`
  and `setCameraEnabled(...)`

## Requirement: Android LiveKit room event propagation

The Android app SHALL observe LiveKit room events and reflect them in shared
call state.

### Scenario: Core connection events update call state

- **WHEN** LiveKit emits `Connected`, `Disconnected`, `FailedToConnect`,
  `Reconnecting`, or `Reconnected`
- **THEN** the repository/ViewModel integration SHALL map those events into app
  connection states `CONNECTED`, `DISCONNECTED`, `FAILED`, or `RECONNECTING`
- **THEN** the UI SHALL observe those states through `LiveData`

### Scenario: Participant and track events update visible media state

- **WHEN** LiveKit emits `ParticipantConnected`, `ParticipantDisconnected`,
  `TrackSubscribed`, `TrackUnsubscribed`, or `ActiveSpeakersChanged`
- **THEN** the shared call state SHALL update participant collections, remote
  video-track bindings, and active-speaker indicators
- **THEN** the active call UI SHALL react without requiring fragment recreation

## Requirement: Android dependency and packaging support for LiveKit

The Android app SHALL include the dependencies and packaging support needed for
LiveKit-based calls.

### Scenario: Gradle repositories and dependencies are configured

- **WHEN** the Android app module is configured for this feature
- **THEN** `frontends/android-app/settings.gradle.kts` SHALL include the JitPack
  repository required by the LiveKit SDK dependency
- **THEN** `frontends/android-app/app/build.gradle.kts` SHALL include LiveKit
  Android SDK version `2.24.1`

### Scenario: Shrinker behavior preserves LiveKit runtime integration

- **WHEN** Android release builds are produced with code shrinking enabled
- **THEN** the app SHALL include any required LiveKit/WebRTC ProGuard rules that
  are not already covered by SDK consumer rules
- **THEN** release packaging SHALL NOT strip classes required for LiveKit room
  connection or media rendering
