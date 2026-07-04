# ADDED Requirements

## Requirement: Web host can start meeting recording from the meeting room toolbar

The system SHALL allow eligible hosts to initiate meeting recording by
activating the record button in the meeting room toolbar, which triggers a
confirmation dialog before calling the backend `startRecording` API, and which
shows a disabled loading state during the API call. Recording SHALL NOT start
without explicit host confirmation.

### Scenario: Host confirms recording start

- **WHEN** a host clicks the record button in the toolbar while in IDLE state
- **THEN** the system SHALL open the recording confirmation dialog

- **WHEN** the host clicks "Start" in the confirmation dialog
- **THEN** the system SHALL call `startRecording`, set `recordingState` to
  `starting`, and disable the toolbar button with a spinner icon

- **WHEN** the LiveKit room metadata becomes `{"recording":true}` and the
  `recordingState` is `starting`
- **THEN** the system SHALL transition `recordingState` to `recording` and show
  the recording indicator to all participants

### Scenario: Host cancels recording start

- **WHEN** a host clicks the record button and then clicks "Cancel" in the
  confirmation dialog
- **THEN** the dialog SHALL close and the `recordingState` SHALL remain `idle`

### Scenario: Start API call fails

- **WHEN** the host confirms recording start and the `startRecording` API call
  fails
- **THEN** the system SHALL display an inline error banner inside the
  confirmation dialog, change the Start button to "Retry", and set
  `recordingState` back to `idle`

### Scenario: Recording start times out without metadata update

- **WHEN** the host has called `startRecording` and the room metadata has not
  updated to `{"recording":true}` within 10 seconds
- **THEN** the system SHALL set `recordingState` to `idle` and set `error` to a
  localized timeout message

## Requirement: Web host can stop active meeting recording from the toolbar

The system SHALL allow eligible hosts to stop the active recording by activating
the record button in the toolbar while in RECORDING state. Stopping executes
immediately without a confirmation dialog.

### Scenario: Host stops recording

- **WHEN** a host clicks the record button while in RECORDING state
- **THEN** the system SHALL call `stopRecording`, set `recordingState` to
  `stopping`, and disable the toolbar button with a spinner icon

- **WHEN** the LiveKit room metadata becomes `{"recording":false}` and the
  `recordingState` is `stopping`
- **THEN** the system SHALL transition `recordingState` to `idle` and remove the
  recording indicator from all participants

### Scenario: Stop API call fails

- **WHEN** the host stops recording and the `stopRecording` API call fails
- **THEN** the system SHALL display an error banner above the toolbar, set
  `recordingState` back to `recording`, and allow the host to retry

## Requirement: Non-host participants cannot control recording

The system SHALL hide the recording toolbar button and the recording
confirmation dialog from non-host participants. Non-host participants SHALL see
the recording indicator when active but SHALL have no control over recording
state.

### Scenario: Non-host sees indicator but not controls

- **WHEN** a non-host participant is in the meeting room
- **THEN** the system SHALL NOT render the recording toolbar button for that
  user and SHALL NOT render the recording confirmation dialog for that user

- **WHEN** recording is active (`recordingState === 'recording'`)
- **THEN** the non-host participant SHALL see the recording indicator

## Requirement: All participants receive recording transition notifications

The system SHALL display an inline banner below the meeting header when
recording transitions to active or inactive, visible to all participants, and
auto-dismissed after a configurable delay.

### Scenario: Recording started notification shown to all

- **WHEN** the `recordingState` transitions from any non-`recording` state to
  `recording`
- **THEN** the system SHALL display the "This meeting is being recorded" banner
  with `role="alert"` and `aria-live="assertive"`, and auto-dismiss it after 8
  seconds

### Scenario: Recording stopped notification shown to all

- **WHEN** the `recordingState` transitions from `recording` to any
  non-`recording` state
- **THEN** the system SHALL display the "Recording stopped" banner with
  `role="status"` and `aria-live="polite"`, and auto-dismiss it after 5 seconds

### Scenario: Banner is not shown on initial mount

- **WHEN** the meeting room mounts with `recordingState` already set to
  `recording` or `idle`
- **THEN** the system SHALL NOT show any recording notification banner on mount

### Scenario: User can manually dismiss recording banner

- **WHEN** the user clicks the dismiss button on a visible recording banner
- **THEN** the system SHALL immediately hide the banner regardless of
  auto-dismiss timer
