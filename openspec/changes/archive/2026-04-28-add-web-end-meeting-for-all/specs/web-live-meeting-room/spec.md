# MODIFIED Requirements

## Requirement: Meeting controls support live-room actions and safe exit

The system SHALL provide a floating control bar for in-call actions, preserve
host-only actions, require confirmation before leaving the meeting, expose a
host-only option to end the meeting for all participants from the leave dialog,
and expose host-only participant moderation actions from the People tab.

### Scenario: Toolbar presents floating primary controls

- **WHEN** the user is in the live meeting room
- **THEN** the system SHALL render a floating centered toolbar with icon-only
  controls, hover tooltips, a distinct end-call action, and a layout-picker
  entry point

### Scenario: Host-only actions are conditionally available

- **WHEN** the current user is recognized as the host in the meeting room
- **THEN** the system SHALL expose host-only actions such as recording controls,
  waiting-room management, participant moderation actions, and the option to end
  the meeting for all participants, and SHALL hide those host-only actions from
  non-host participants

### Scenario: Non-host leave action requires confirmation and disconnects media

- **WHEN** a non-host user activates the leave-call control and confirms the
  destructive action
- **THEN** the system SHALL disconnect from the LiveKit room before navigating
  the user back to `/${locale}/workspace`

### Scenario: Host chooses to leave without ending the meeting

- **WHEN** the host activates the leave-call control and selects the leave-only
  option
- **THEN** the system SHALL disconnect from the LiveKit room before navigating
  the host back to `/${locale}/workspace` without calling the meeting end API

### Scenario: Host ends the meeting for all participants successfully

- **WHEN** the host activates the leave-call control and confirms the
  end-for-all option
- **THEN** the system SHALL call the meeting end API for the active meeting,
  keep the dialog in a submitting state until the request completes, and after a
  successful response disconnect from the LiveKit room before navigating the
  host back to `/${locale}/workspace`

### Scenario: Host end-meeting request fails

- **WHEN** the host chooses the end-for-all option and the meeting end API
  request fails
- **THEN** the system SHALL keep the leave dialog open, show localized inline
  error feedback, re-enable available actions after the request completes, and
  allow the host to retry ending the meeting or leave locally instead
