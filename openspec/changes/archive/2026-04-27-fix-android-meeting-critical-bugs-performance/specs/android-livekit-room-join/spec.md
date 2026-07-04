# MODIFIED Requirements

## Requirement: Android LiveKit room repository

The Android app SHALL manage LiveKit room lifecycle through a dedicated
repository abstraction.

### Scenario: Room repository creates and connects LiveKit room

- **WHEN** `CallViewModel` requests a room connection with URL, token, and
  desired initial local media states
- **THEN** the `LiveKitRepository` implementation SHALL create a room using
  `LiveKit.create()`
- **THEN** it SHALL call `room.connect()` with the configured server URL and
  token
- **THEN** after connection success is confirmed, it SHALL apply the requested
  initial microphone and camera enabled states before exposing steady connected
  state
- **THEN** the implementation SHALL use the LiveKit server URL from
  `BuildConfig`

### Scenario: Room repository supports disconnect and local AV controls

- **WHEN** the user ends a call or toggles local media
- **THEN** `LiveKitRepository` SHALL expose operations for disconnect,
  microphone enable/disable, and camera enable/disable
- **THEN** local media toggles SHALL call LiveKit's `setMicrophoneEnabled(...)`
  and `setCameraEnabled(...)`

### Scenario: Local media toggle called before room availability is handled safely

- **WHEN** microphone or camera toggle methods are invoked while the room or
  local participant is not yet available
- **THEN** repository methods SHALL no-op safely and log a warning
- **THEN** no exception SHALL be thrown to callers

### Scenario: Camera switch is implemented through local camera track

- **WHEN** `switchCamera()` is invoked during an active room session
- **THEN** the repository SHALL resolve the active local camera track and switch
  between front and back camera position using LiveKit-supported camera-position
  APIs
- **THEN** if no switchable local camera track exists, the operation SHALL no-op
  safely with diagnostic logging
