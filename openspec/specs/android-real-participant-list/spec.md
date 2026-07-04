# Purpose

Define the Android active-call participant sheet behavior when driven by real
LiveKit participant state and backend role enrichment.

# ADDED Requirements

## Requirement: Participants sheet uses real call participant sources

The Android participants sheet SHALL be driven by real participant data instead
of static mock entries.

### Scenario: LiveKit data is primary participant source

- **WHEN** `ParticipantsBottomSheet` is opened during an active call
- **THEN** `ParticipantsViewModel` SHALL consume live participant state from
  `CallViewModel` LiveData
- **THEN** participant rows SHALL reflect current mic and camera states from
  LiveKit updates

### Scenario: Backend role enrichment runs once per sheet session

- **WHEN** `ParticipantsBottomSheet` initializes participant data load for
  current meeting
- **THEN** `ParticipantsViewModel` SHALL call
  `GET /api/v1/meetings/{id}/participants` one time for role enrichment
- **THEN** enrichment results SHALL be merged into the LiveKit-driven list
  without replacing real-time media state

## Requirement: Participant merge behavior and fallback

The Android participant merge logic SHALL preserve call-state correctness while
enriching role metadata.

### Scenario: Merge uses stable identity-only participant matching

- **WHEN** combining LiveKit participant entries with backend participant
  records
- **THEN** the system SHALL use stable participant identity/id matching as the
  sole role-resolution key
- **THEN** it SHALL NOT use display-name fallback matching for role assignment

### Scenario: Unmatched LiveKit participant defaults role

- **WHEN** a LiveKit participant has no corresponding backend participant record
- **THEN** the merged participant model SHALL assign role `PARTICIPANT`
- **THEN** the participant SHALL remain visible with current media state

### Scenario: Enrichment failure does not block participant list

- **WHEN** `GET /api/v1/meetings/{id}/participants` fails
- **THEN** `ParticipantsViewModel` SHALL still publish LiveKit-only participant
  list
- **THEN** role badge rendering SHALL degrade gracefully without blocking
  bottom-sheet usage

## Requirement: Participant domain model and adapter role rendering

The Android participant domain and UI bindings SHALL represent production
participant metadata.

### Scenario: Participant model fields are refactored

- **WHEN** the participant domain model is consumed by participant UI
- **THEN** the model SHALL include participant id, display name, mic state,
  camera state, and role enum values `HOST`, `PARTICIPANT`, `GUEST`
- **THEN** mock-only fields (`roleStatus`, `connectionStatus`, `hasAlert`) SHALL
  NOT be part of the production participant model

### Scenario: Participant row displays role badge

- **WHEN** `ParticipantAdapter` binds a merged participant entry
- **THEN** host entries SHALL display a host role badge
- **THEN** guest entries SHALL display a guest role badge
- **THEN** default participant role SHALL render without host or guest badge

## Requirement: Participants bottom sheet and viewmodel integration

The Android participants sheet SHALL integrate activity-scoped call state with
injected repository-backed viewmodel dependencies.

### Scenario: Bottom sheet uses shared call viewmodel

- **WHEN** `ParticipantsBottomSheet` is attached to `VideoCallActivity`
- **THEN** it SHALL obtain `CallViewModel` from activity scope
- **THEN** it SHALL pass current LiveKit participant stream context to
  `ParticipantsViewModel`

### Scenario: Viewmodel uses injected dependencies

- **WHEN** `ParticipantsViewModel` is instantiated
- **THEN** it SHALL use injected dependencies instead of a zero-argument
  constructor
- **THEN** it SHALL not initialize or publish mock participant datasets
