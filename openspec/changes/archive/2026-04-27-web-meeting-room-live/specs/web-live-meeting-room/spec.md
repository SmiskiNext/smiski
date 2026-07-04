# ADDED Requirements

## Requirement: Web meeting room connects to LiveKit with persisted join credentials

The system SHALL initialize the web meeting room from the previously stored
meeting session credentials, join the corresponding LiveKit room, and keep the
room shell synchronized with the LiveKit connection lifecycle.

### Scenario: Join meeting with persisted credentials

- **WHEN** a user opens the web meeting room after a successful join or
  meeting-creation flow and valid room credentials exist in session storage
- **THEN** the system connects to the matching LiveKit room and renders the live
  meeting shell instead of static participant mockups

### Scenario: Missing credentials block live room startup

- **WHEN** the meeting room is opened without the required stored room token or
  room name
- **THEN** the system MUST avoid mounting the live room with invalid state and
  MUST surface a recoverable failure path for the user

### Scenario: Connection state changes are reflected in the shell

- **WHEN** the LiveKit connection state changes between connected, reconnecting,
  and disconnected
- **THEN** the system SHALL update the meeting UI to reflect the active
  connection state and related messaging

## Requirement: Web meeting room renders live participant media and speaking state

The system SHALL render meeting participants from LiveKit room state, showing
camera video when available and a non-video fallback when camera publishing is
unavailable, while also surfacing active-speaker state.

### Scenario: Participant camera is enabled

- **WHEN** a participant has a subscribed camera track available
- **THEN** the participant tile SHALL render the live video in a cover-fit
  layout with the participant identity overlay

### Scenario: Participant camera is disabled

- **WHEN** a participant does not have an active camera track
- **THEN** the participant tile SHALL render a fallback avatar or initials
  presentation together with participant labeling

### Scenario: Participant becomes an active speaker

- **WHEN** LiveKit marks a participant as actively speaking
- **THEN** the system SHALL visually emphasize that participant tile and show
  the speaking indicator state

## Requirement: Web meeting room supports responsive layouts and self preview

The system SHALL arrange remote participants according to the selected meeting
layout and viewport constraints while keeping the local self-view visible as a
floating preview.

### Scenario: Auto layout adapts to participant count

- **WHEN** the room is in auto layout mode
- **THEN** the system SHALL use 1 column for 1 participant, 2 columns for 2
  through 4 participants, 3 columns for 5 through 9 participants, and 4 columns
  for 10 or more participants before applying responsive viewport caps

### Scenario: Narrow screens cap grid density

- **WHEN** the meeting room is rendered below the tablet and phone breakpoints
- **THEN** the system SHALL cap the participant grid to at most 2 columns below
  768px and at most 1 column below 480px

### Scenario: Self preview remains visible while in the meeting

- **WHEN** the local participant is connected to the room
- **THEN** the system SHALL render a floating self-view preview in the grid area
  that shows local camera video when enabled and a local fallback state when
  disabled

### Scenario: User selects a non-auto layout

- **WHEN** the user selects tiled, spotlight, or sidebar layout from the layout
  picker
- **THEN** the system SHALL update the room arrangement to match the selected
  layout mode until the user changes it again

## Requirement: Meeting shell surfaces connection feedback and call duration

The system SHALL present meeting-level status indicators that help users
understand connectivity and elapsed call time while remaining accessible.

### Scenario: Connected status is displayed accessibly

- **WHEN** the room is connected
- **THEN** the meeting header SHALL show the connected status indicator using
  the primary visual treatment and SHALL expose status updates through an
  `aria-live` polite region

### Scenario: Reconnecting status is shown inline

- **WHEN** the room transitions into reconnecting state
- **THEN** the system SHALL show reconnecting status in the header and an inline
  reconnecting message within the meeting shell

### Scenario: Call timer advances during the session

- **WHEN** the user remains in the meeting room
- **THEN** the system SHALL display elapsed call time that updates continuously
  and is formatted as MM:SS or H:MM:SS for sessions longer than one hour

## Requirement: Meeting controls support live-room actions and safe exit

The system SHALL provide a floating control bar for in-call actions, preserve
host-only actions, and require confirmation before leaving the meeting.

### Scenario: Toolbar presents floating primary controls

- **WHEN** the user is in the live meeting room
- **THEN** the system SHALL render a floating centered toolbar with icon-only
  controls, hover tooltips, a distinct end-call action, and a layout-picker
  entry point

### Scenario: Host-only actions are conditionally available

- **WHEN** the current user is recognized as the host in the meeting room
- **THEN** the system SHALL expose host-only actions such as recording controls
  and waiting-room management, and SHALL hide them from non-host participants

### Scenario: Leave action requires confirmation and disconnects media

- **WHEN** the user activates the leave-call control and confirms the
  destructive action
- **THEN** the system SHALL disconnect from the LiveKit room before navigating
  the user back to `/workspace`

## Requirement: Hosts can manage the waiting room with live updates

The system SHALL allow eligible hosts to review and act on pending join requests
from the meeting room, using the existing join-request APIs and SSE
notifications.

### Scenario: Host sees pending waiting-room requests

- **WHEN** waiting-room support is enabled for the meeting and the host opens
  the waiting-room management UI
- **THEN** the system SHALL load and display the current pending join requests,
  including loading, empty, populated, and recoverable error states

### Scenario: Host approves, denies, or approves all requests

- **WHEN** the host performs an approve, deny, or approve-all action on pending
  requests
- **THEN** the system SHALL call the matching generated SDK operation and update
  the waiting-room list to reflect the authoritative backend result

### Scenario: New join request arrives while host is in the room

- **WHEN** the backend emits a `join_request_created` SSE event for the meeting
- **THEN** the system SHALL update the host-facing pending count and refresh the
  waiting-room state so the new request is visible without a page reload

### Scenario: SSE disconnects during host waiting-room monitoring

- **WHEN** the waiting-room event stream is interrupted while the host remains
  in the room
- **THEN** the system SHALL attempt to restore live waiting-room updates and
  SHALL keep the host capable of manually refreshing or retrying pending-request
  retrieval
