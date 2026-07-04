# ADDED Requirements

## Requirement: In-call control correctness must match executable behavior

The Android in-call UI SHALL expose only controls whose underlying repository
behavior is implemented and successful.

### Scenario: Mute-all action is unavailable without backend support

- **WHEN** `muteAllParticipants()` has no backend-backed implementation
- **THEN** the participants sheet SHALL hide or disable the mute-all action
- **THEN** tapping the unavailable control SHALL perform no action and SHALL NOT
  show success feedback

### Scenario: Camera flip performs actual local camera switch

- **WHEN** user taps the flip-camera action during an active call with local
  video enabled
- **THEN** `LiveKitRepositoryImpl.switchCamera()` SHALL switch local camera
  position between front and back using LiveKit local camera track APIs
- **THEN** the active call UI SHALL reflect the resulting camera position
  without requiring rejoin

## Requirement: Participant and waiting-room rendering updates must avoid full-list rebinding

High-frequency call-state updates SHALL use targeted adapter updates to minimize
unnecessary bind work.

### Scenario: Participant list uses DiffUtil updates

- **WHEN** participant list data changes in `ParticipantsViewModel`
- **THEN** `ParticipantAdapter` SHALL update rows via
  `ListAdapter.submitList(...)` with `DiffUtil.ItemCallback`
- **THEN** identity equality SHALL be based on participant ID and content
  equality SHALL include display name, role, microphone state, and camera state

### Scenario: Waiting-room join-request list uses DiffUtil updates

- **WHEN** pending join requests change while the waiting-room sheet is open
- **THEN** `JoinRequestAdapter` SHALL update rows via
  `ListAdapter.submitList(...)` with `DiffUtil.ItemCallback`
- **THEN** identity equality SHALL be based on join-request ID

### Scenario: Active speaker highlight updates only changed tiles

- **WHEN** active-speaker set changes in the video grid
- **THEN** the adapter SHALL compute positions whose active-speaker status
  changed and call `notifyItemChanged()` only for those items
- **THEN** the adapter SHALL NOT call `notifyDataSetChanged()` for
  active-speaker-only changes

## Requirement: Active speaker updates must not trigger duplicate participant-list refresh

Active-speaker changes SHALL propagate through a dedicated speaker-update path
without rebuilding participant list state.

### Scenario: Single emission path for active-speaker changes

- **WHEN** LiveKit emits an active-speaker update
- **THEN** repository logic SHALL trigger `onActiveSpeakersChanged(...)`
  callbacks only
- **THEN** repository logic SHALL NOT emit participant-list refresh
  notifications when participant identity/media fields are unchanged

## Requirement: Repository boundaries must handle malformed UUID inputs safely

Waiting-room repository methods SHALL treat UUID parse failures as handled
errors.

### Scenario: Invalid UUID input does not crash waiting-room operations

- **WHEN** any waiting-room repository method receives a non-UUID string that
  fails `UUID.fromString(...)`
- **THEN** the method SHALL catch `IllegalArgumentException` in addition to
  `IOException`
- **THEN** the method SHALL return existing error/empty-result patterns without
  propagating runtime exceptions
