# MODIFIED Requirements

## Requirement: Meeting controls support live-room actions and safe exit

The system SHALL provide a floating control bar for in-call actions, preserve
host-only actions, require confirmation before leaving the meeting, and expose
host-only participant moderation actions from the People tab.

### Scenario: Toolbar presents floating primary controls

- WHEN the user is in the live meeting room
- THEN the system SHALL render a floating centered toolbar with icon-only
  controls, hover tooltips, a distinct end-call action, and a layout-picker
  entry point

### Scenario: Host-only actions are conditionally available

- WHEN the current user is recognized as the host in the meeting room
- THEN the system SHALL expose host-only actions such as recording controls,
  waiting-room management, and participant moderation actions, and SHALL hide
  them from non-host participants

### Scenario: Leave action requires confirmation and disconnects media

- WHEN the user activates the leave-call control and confirms the destructive
  action
- THEN the system SHALL disconnect from the LiveKit room before navigating the
  user back to `/workspace`

# ADDED Requirements

## Requirement: People tab reflects host-aware participant moderation state

The system SHALL render the People tab participant list with enough
host-awareness to preserve read-only participant status for non-host users while
enabling host-only moderation affordances and feedback.

### Scenario: Participant rows identify the meeting host

- **WHEN** the meeting room builds participant view models for the sidebar
  People tab
- **THEN** the system SHALL mark the participant whose identity matches the
  meeting host identity as `HOST` so moderation controls can be suppressed on
  that row

### Scenario: Non-host People tab remains read-only

- **WHEN** a non-host user opens the People tab
- **THEN** the system SHALL continue showing participant presence and media
  status without rendering interactive mute controls

### Scenario: Moderation copy is localized in supported languages

- **WHEN** the People tab renders mute-all labels, per-participant moderation
  tooltips, or moderation failure feedback
- **THEN** the system SHALL source that copy from the localized meeting-room
  message catalog for all supported web meeting room languages
