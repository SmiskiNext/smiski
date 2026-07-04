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

## Requirement: Cancel scheduled meeting from schedule/edit screen

The Android app SHALL provide a host-only action to cancel a SCHEDULED meeting
from the schedule/edit screen (ScheduleFragment in edit mode). Invoking the
action SHALL call the existing backend cancel-meeting API and navigate back with
a result on success.

### Scenario: Host sees cancel button in edit mode

- **WHEN** a host opens the ScheduleFragment in edit mode for a SCHEDULED
  meeting
- **THEN** the system SHALL display a "Cancel Meeting" button that is not shown
  for non-hosts or meetings in states other than SCHEDULED

### Scenario: Non-host does not see cancel button

- **WHEN** a non-host user opens a meeting in the ScheduleFragment edit view
- **THEN** the system SHALL NOT display the "Cancel Meeting" button

### Scenario: Cancel confirmation dialog

- **WHEN** the host taps the "Cancel Meeting" button
- **THEN** the system SHALL present a Material confirmation dialog warning that
  the action is irreversible before proceeding

### Scenario: Cancel meeting success

- **WHEN** the host confirms cancellation and the backend cancel-meeting API
  returns success
- **THEN** the system SHALL navigate back to the Dashboard and trigger a meeting
  list refresh

### Scenario: Cancel meeting failure

- **WHEN** the backend cancel-meeting API returns an error during host
  cancellation
- **THEN** the system SHALL display a Snackbar with a descriptive error message
  and remain on the schedule/edit screen so the host can retry

### Scenario: Cancel meeting loading state

- **WHEN** a cancel request is in flight
- **THEN** the system SHALL disable the cancel button and show a progress
  indicator to prevent duplicate submissions
