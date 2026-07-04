# MODIFIED Requirements

## Requirement: Host leave behavior in active call

The active-call leave flow SHALL branch based on whether the current user is the
meeting host. Non-host users SHALL continue to see the existing single-option
leave confirmation dialog. Host users SHALL see a two-option dialog that
distinguishes between leaving locally and ending the meeting for all
participants.

### Scenario: Non-host taps end-call button

- **WHEN** a non-host user taps the end-call button during an active call
- **THEN** the system SHALL present a confirmation dialog with a single "Leave
  Meeting" action and a cancel option, matching prior behavior

### Scenario: Host taps end-call button

- **WHEN** a host user taps the end-call button during an active call
- **THEN** the system SHALL present a dialog with two primary options: "Leave
  Meeting" (local disconnect only) and "End Meeting for All" (triggers backend
  termination)

### Scenario: Host selects local leave

- **WHEN** a host selects "Leave Meeting" from the host leave dialog
- **THEN** the system SHALL disconnect from the room and finish
  VideoCallActivity, identical to the existing non-host leave path

### Scenario: Host selects end meeting for all

- **WHEN** a host selects "End Meeting for All" from the host leave dialog
- **THEN** the system SHALL initiate the end-meeting backend flow as defined in
  the android-host-meeting-termination capability
