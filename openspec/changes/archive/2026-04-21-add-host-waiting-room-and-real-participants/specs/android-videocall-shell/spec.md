# MODIFIED Requirements

## Requirement: ActiveCallFragment

The `ActiveCallFragment` SHALL display a LiveKit-backed active video call UI
with compact controls, layout switching, host-aware action surfaces, and host
waiting-room access.

### Scenario: ActiveCall UI elements

- **WHEN** ActiveCallFragment is displayed
- **THEN** it SHALL show:
    - A RecyclerView-based participant video grid
    - A draggable self-view overlay sized for picture-in-picture style preview
    - A top bar with call timer, connection quality indicator, participant
      count, and a layout-switch entry point
    - Floating call controls for microphone, camera, more-actions, and end call
    - Secondary entry points for chat, participants, screen sharing, and
      host-only settings through the meeting actions surface
    - A host-only waiting-room toolbar action with pending badge when waiting
      room is enabled for the meeting

### Scenario: Dynamic video tiles reflect participant state

- **WHEN** participant or track state changes during the room session
- **THEN** the grid SHALL update tile count and span layout dynamically or
  according to the selected layout mode
- **THEN** each tile SHALL support participant name overlay, camera-off
  placeholder, muted-mic badge, and active-speaker border state

### Scenario: Controls auto-hide and can be restored

- **WHEN** the call controls are shown during the active call
- **THEN** they SHALL auto-hide after 3 seconds of inactivity
- **THEN** a tap on the call surface SHALL show the controls again

### Scenario: End call action

- **WHEN** user taps "End Call" button
- **THEN** the system SHALL disconnect from the LiveKit room before finishing
- **THEN** system SHALL finish `VideoCallActivity`
- **THEN** user returns to previous app/task (MainActivity or WelcomeActivity)

## Requirement: CallViewModel

A shared `CallViewModel` SHALL manage backend join, password-gating lookup
state, LiveKit room state, selected layout, meeting-settings state, host
waiting-room SSE lifecycle, and waiting-room pending state exposure across call
fragments.

### Scenario: ViewModel scope

- **WHEN** `CallViewModel` is created
- **THEN** it SHALL be scoped to `VideoCallActivity` (survives fragment
  transitions)
- **THEN** it SHALL use `@HiltViewModel` annotation

### Scenario: Call state management

- **WHEN** `CallViewModel` is initialized
- **THEN** it SHALL expose:
    - `isMicEnabled` (LiveData<Boolean>)
    - `isCameraEnabled` (LiveData<Boolean>)
    - `meetingCode` (LiveData<String>)
    - `displayName` (LiveData<String>) for guest mode
    - password-gating state including whether the current meeting requires a
      password, the current password value, and meeting-lookup loading/error
      signals
    - room connection state (`DISCONNECTED`, `CONNECTING`, `CONNECTED`,
      `RECONNECTING`, `FAILED`)
    - a LiveData participant collection for visible room participants
    - local and remote video-track state needed by the active call UI
    - `currentLayout` for the selected `VideoLayout`
    - `meetingSettings` for the latest editable meeting settings snapshot
    - `isHost` for host-only UI branching
    - host waiting-room pending list or count state consumed by active-call host
      UI

### Scenario: Meeting lookup orchestrates protected join state

- **WHEN** `fetchMeetingInfoAndJoin(shortCode)` is called with a valid meeting
  code
- **THEN** `CallViewModel` SHALL fetch meeting detail by short code and cache
  the resolved meeting UUID for the current join attempt
- **THEN** it SHALL publish whether the current meeting requires a password
- **THEN** it SHALL only proceed to join submission immediately when the meeting
  does not require a password

### Scenario: Protected join submission includes password state

- **WHEN** `requestJoinRoom()` is called after a protected meeting lookup
- **THEN** `CallViewModel` SHALL pass the current password value to
  `JoinRoomRepository.requestJoin(...)`
- **THEN** it SHALL preserve the existing approved, pending, denied, and expired
  join-state handling

### Scenario: Resetting join state clears password-specific state

- **WHEN** the pre-join flow is reset for retry or cancellation
- **THEN** `CallViewModel` SHALL clear password value, password-required state,
  and meeting-lookup error/loading state
- **THEN** the next join attempt SHALL start from a clean pre-lookup state

### Scenario: Room and settings methods coordinate call control

- **WHEN** the app has a valid LiveKit token for the current meeting
- **THEN** `CallViewModel` SHALL provide `connectToRoom(url, token)` to start
  the room connection
- **THEN** `toggleLocalMic()` and `toggleLocalCamera()` SHALL update both UI
  state and LiveKit local participant media state
- **THEN** `setVideoLayout()` SHALL publish layout changes to the active-call UI
- **THEN** `loadMeetingSettings()` and `updateMeetingSettings()` SHALL
  coordinate host settings retrieval and replacement for the current meeting
- **THEN** `endCall()` SHALL disconnect from the LiveKit room and stop call
  timers or related resources

### Scenario: Host waiting-room SSE is bound to active call lifecycle

- **WHEN** host enters an active call for a waiting-room-enabled meeting
- **THEN** `CallViewModel` SHALL connect host meeting SSE and process supported
  event types
- **WHEN** host call ends or leaves active call
- **THEN** `CallViewModel` SHALL disconnect host meeting SSE and stop reconnect
  scheduling

### Scenario: Waiting-room state is resynchronized after reconnect

- **WHEN** host meeting SSE reconnects after interruption
- **THEN** `CallViewModel` SHALL trigger pending join-request list
  synchronization
- **THEN** it SHALL expose synchronized pending state for waiting-room badge and
  sheet consumers

## Requirement: Participants BottomSheet

The `ParticipantsBottomSheet` SHALL display merged real participant data over
the active call.

### Scenario: Show participants

- **WHEN** user selects the Participants action from the active call meeting
  actions surface
- **THEN** `ParticipantsBottomSheet` is shown as modal bottom sheet
- **THEN** it displays merged participant rows using LiveKit real-time state and
  backend role enrichment

### Scenario: Dismiss participants

- **WHEN** user swipes down or taps outside ParticipantsBottomSheet
- **THEN** the bottom sheet dismisses
- **THEN** ActiveCallFragment remains visible
