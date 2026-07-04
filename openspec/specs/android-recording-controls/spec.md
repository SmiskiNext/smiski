# ADDED Requirements

## Requirement: Hosts can control meeting recording from the Android active-call UI

The Android active-call UI SHALL provide a dedicated recording control for hosts
that starts and stops the existing backend meeting recording workflow while
preventing duplicate actions during in-flight requests.

### Scenario: Host sees the dedicated record control

- **WHEN** `ActiveCallFragment` is displayed for a participant whose `isHost`
  state is true
- **THEN** the floating control bar SHALL show a dedicated record control
  between the more-actions control and the end-call control
- **AND** the control SHALL be hidden for participants whose `isHost` state is
  false

### Scenario: Host starts a recording successfully

- **WHEN** the host taps the record control while recording is inactive
- **THEN** the Android app SHALL call the existing start-recording backend
  endpoint for the active meeting
- **AND** the control SHALL enter a loading state that disables repeated taps
  until the request completes
- **AND** after the backend confirms recording start, the control SHALL render
  the active recording state

### Scenario: Host stops a recording successfully

- **WHEN** the host taps the record control while recording is active
- **THEN** the Android app SHALL call the existing stop-recording backend
  endpoint for the active meeting
- **AND** the control SHALL enter a loading state that disables repeated taps
  until the request completes
- **AND** if the stop request succeeds, the control SHALL remain in the
  recording state until room metadata reports that recording has finalized

### Scenario: Start recording fails

- **WHEN** the host start-recording request fails because of backend,
  validation, or network error
- **THEN** the Android app SHALL show a Snackbar with an error message
- **AND** the control SHALL exit the loading state
- **AND** the control SHALL return to the inactive recording state

### Scenario: Stop recording fails

- **WHEN** the host stop-recording request fails because of backend, validation,
  or network error
- **THEN** the Android app SHALL show a Snackbar with an error message
- **AND** the control SHALL exit the loading state
- **AND** the control SHALL remain in the active recording state so the host can
  retry

## Requirement: All Android participants see live recording status in the meeting UI

The Android app SHALL show a persistent recording indicator to all meeting
participants whenever the active room metadata reports that recording is in
progress.

### Scenario: Participant sees active recording indicator

- **WHEN** room metadata indicates `recording=true` during an active meeting
- **THEN** the top bar SHALL display a red-dot recording indicator with `REC`
  text for every participant in the room
- **AND** the red dot SHALL use a pulsing animation while the indicator is
  visible

### Scenario: Participant joins while recording is already active

- **WHEN** a participant joins a room whose metadata already indicates
  `recording=true`
- **THEN** the app SHALL show the recording indicator without requiring any host
  action after that participant joins

### Scenario: Recording reaches a terminal state

- **WHEN** room metadata changes from `recording=true` to `recording=false`
- **THEN** the Android app SHALL hide the top-bar recording indicator for all
  participants
- **AND** any host recording control loading state SHALL be cleared

## Requirement: Backend recording lifecycle publishes room metadata state

The backend recording workflow SHALL update LiveKit room metadata so clients can
observe the current active-recording state without additional event channels.

### Scenario: Successful recording start publishes active metadata

- **WHEN** `StartRecordingUseCase` completes a successful recording start with
  LiveKit egress running
- **THEN** the backend SHALL update the LiveKit room metadata for that meeting
  room to indicate `recording=true`

### Scenario: Recording finalization clears active metadata

- **WHEN** `FinalizeRecordingUseCase` handles recording completion or failure
- **THEN** the backend SHALL update the LiveKit room metadata for that meeting
  room to indicate `recording=false`

### Scenario: Stop request relies on finalization for metadata clear

- **WHEN** `StopRecordingUseCase` accepts a stop request for an active recording
- **THEN** the backend SHALL NOT clear room metadata in that use case
- **AND** clients SHALL continue to treat recording as active until finalization
  updates the room metadata

## Requirement: Android meeting state reacts to LiveKit room metadata changes

The Android in-call state management SHALL map LiveKit room metadata changes
into recording UI state without introducing a separate recording event
transport.

### Scenario: Polling detects room metadata changes

- **WHEN** `LiveKitRepositoryImpl.checkRoomStateChanges()` detects that room
  metadata has changed
- **THEN** it SHALL notify the call-state listener through a dedicated
  room-metadata callback with the latest metadata payload

### Scenario: CallViewModel derives recording state from metadata

- **WHEN** `CallViewModel` receives a room metadata callback during an active
  meeting
- **THEN** it SHALL parse the metadata payload and update its recording state
  LiveData based on the `recording` boolean value
- **AND** malformed or empty metadata SHALL be treated as recording inactive
  rather than crashing the call flow

### Scenario: Host action loading and room-wide recording state remain independent

- **WHEN** the host submits a recording action and room metadata has not yet
  reached the matching terminal state
- **THEN** the ViewModel SHALL track request loading separately from the
  room-wide recording-active flag
- **AND** the participant-visible indicator SHALL follow room metadata rather
  than the request-loading state
