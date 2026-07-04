# MODIFIED Requirements

## Requirement: Participant domain model and adapter role rendering

The Android participant domain and UI bindings SHALL represent production
participant roles and media states, including host-only mute affordances.

The `ParticipantAdapter` SHALL accept an `isHost` boolean flag and a
`ParticipantMuteListener` callback interface. When `isHost` is `true`, the
adapter SHALL render clickable mute affordances (mic and camera buttons) on
participant rows where the target participant is not local and does not have the
HOST role. Tapping a mic affordance SHALL invoke
`ParticipantMuteListener.onMuteMic(identity)`. Tapping a camera affordance SHALL
invoke `ParticipantMuteListener.onMuteCamera(identity)`.

When `isHost` is `false`, the mic and camera icons SHALL remain non-interactive
display-only indicators (existing behavior).

### Scenario: Host sees mute buttons on non-host non-local participants

- **WHEN** the local user is the host and the participants sheet is open
- **THEN** each participant row for a non-local, non-host participant SHALL show
  tappable mic and camera icons
- **THEN** tapping the mic icon SHALL call
  `ParticipantMuteListener.onMuteMic(identity)` with that participant's LiveKit
  identity
- **THEN** tapping the camera icon SHALL call
  `ParticipantMuteListener.onMuteCamera(identity)` with that participant's
  LiveKit identity

### Scenario: Non-host sees read-only media state indicators

- **WHEN** the local user is not the host and the participants sheet is open
- **THEN** mic and camera icons SHALL be non-clickable display indicators with
  no listener attached

### Scenario: Host row and local row have no mute affordances

- **WHEN** the host views their own row or another host's row in the
  participants sheet
- **THEN** no mute affordance is rendered for that row
