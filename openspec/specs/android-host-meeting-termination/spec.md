# ADDED Requirements

## Requirement: End meeting for all from active call

The Android app SHALL provide a host-only action to end an active (LIVE) meeting
for all participants from within the active call screen. Invoking the action
SHALL call the backend end-meeting API, and the app SHALL finalize the call only
after receiving a successful response.

### Scenario: Host sees end-meeting option in leave dialog

- **WHEN** a user who is the meeting host taps the leave/end-call button during
  an active call
- **THEN** the system SHALL display a dialog offering two distinct choices:
  "Leave Meeting" (local only) and "End Meeting for All" (terminates for
  everyone)

### Scenario: Non-host sees standard leave dialog

- **WHEN** a user who is NOT the meeting host taps the leave/end-call button
  during an active call
- **THEN** the system SHALL display the existing single-option leave
  confirmation dialog unchanged

### Scenario: End meeting loading state

- **WHEN** a host confirms "End Meeting for All"
- **THEN** the system SHALL enter a loading state that prevents duplicate
  requests and remains inside the active call screen until the backend responds

### Scenario: End meeting success

- **WHEN** the backend end-meeting API returns a successful response
- **THEN** the system SHALL disconnect from the LiveKit room and finish the
  VideoCallActivity

### Scenario: End meeting failure

- **WHEN** the backend end-meeting API returns an error
- **THEN** the system SHALL remain inside the active call screen, clear the
  loading state, and display a recoverable error message to the host without
  disconnecting

### Scenario: End meeting precondition failure

- **WHEN** the host taps "End Meeting for All" but the meeting ID or host status
  is missing from ViewModel state
- **THEN** the system SHALL emit a recoverable error message and NOT attempt a
  backend call
