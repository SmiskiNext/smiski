# ADDED Requirements

## Requirement: Hosts can mute participant media from the People tab

The system SHALL let meeting hosts moderate participant media from the People
tab by exposing mute controls for non-local, non-host participant rows and
invoking the existing backend moderation APIs.

### Scenario: Host sees per-participant moderation controls

- **WHEN** the current user is the meeting host and the People tab renders a
  participant row for a remote participant who is not marked as `HOST`
- **THEN** the system SHALL show microphone and camera mute buttons for that
  participant row together with contextual tooltips

### Scenario: Host does not see moderation controls on protected rows

- **WHEN** the current user is the meeting host and the People tab renders
  either the local participant row or a participant row marked as `HOST`
- **THEN** the system SHALL hide the per-participant moderation buttons for that
  row

### Scenario: Host mutes an individual participant track

- **WHEN** the host activates the microphone or camera mute control for an
  eligible participant row
- **THEN** the system SHALL call the corresponding generated moderation endpoint
  for that participant and SHALL show loading feedback only on the activated
  control until the request completes

### Scenario: Participant media state updates from LiveKit after moderation

- **WHEN** a moderation request completes and LiveKit emits the authoritative
  muted-track state for the affected participant
- **THEN** the system SHALL update the participant row media indicators from the
  LiveKit room state rather than from an optimistic UI toggle

### Scenario: Individual moderation request fails

- **WHEN** the moderation API request for a participant microphone or camera
  fails
- **THEN** the system SHALL restore the control from loading state and SHALL
  surface a recoverable mute-action failure message

## Requirement: Hosts can mute all non-host participant microphones from the People tab

The system SHALL provide a host-only bulk microphone moderation action above the
People tab participant list that uses the existing backend mute-all endpoint.

### Scenario: Host sees the mute-all banner

- **WHEN** the current user is the meeting host and opens the People tab
- **THEN** the system SHALL render a sticky sub-header between the tab bar and
  the participant list containing a full-width mute-all action

### Scenario: Non-host does not see the mute-all banner

- **WHEN** the current user is not the meeting host and opens the People tab
- **THEN** the system SHALL not render the bulk mute-all action

### Scenario: Host mutes all participants

- **WHEN** the host activates the mute-all action
- **THEN** the system SHALL call the generated mute-all endpoint, SHALL show a
  loading state while the request is pending, and SHALL show a transient success
  state after a successful response before returning to the default label

### Scenario: Mute-all request fails

- **WHEN** the mute-all API request fails
- **THEN** the system SHALL return the action to its default state and SHALL
  surface a recoverable mute-action failure message
