# MODIFIED Requirements

## Requirement: PreJoinFragment

The `PreJoinFragment` SHALL handle pre-call setup and backend room-join
orchestration for both guest and authenticated users.

### Scenario: Guest mode display

- **WHEN** `VideoCallActivity` is launched with `isGuest=true`
- **THEN** PreJoinFragment SHALL display a "Display Name" input field
- **THEN** the field SHALL be required before joining

### Scenario: Authenticated mode display

- **WHEN** `VideoCallActivity` is launched with `isGuest=false`
- **THEN** PreJoinFragment SHALL NOT display "Display Name" field
- **THEN** user's name from profile SHALL be used automatically

### Scenario: PreJoin UI elements

- **WHEN** PreJoinFragment is displayed
- **THEN** it SHALL show:
    - Camera preview surface or placeholder
    - Meeting code input field
    - Microphone toggle switch
    - Camera toggle switch
    - "Join" button

### Scenario: Validation before join

- **WHEN** user taps "Join" without meeting code
- **THEN** system SHALL show inline error on meeting code field
- **WHEN** guest user taps "Join" without display name
- **THEN** system SHALL show inline error on display name field

### Scenario: Join request approval navigates into active call

- **WHEN** the pre-join flow receives an approved backend join result directly
  or through SSE approval
- **THEN** the fragment SHALL persist the selected mic/camera preference state
- **THEN** the fragment SHALL navigate via `action_prejoin_to_activeCall`
- **THEN** the LiveKit token required for room connection SHALL be handed off to
  the shared `CallViewModel`

### Scenario: Join request pending state is visible to the user

- **WHEN** the backend join request returns `PENDING`
- **THEN** PreJoinFragment SHALL show a waiting indicator or dialog while SSE is
  active
- **THEN** the user SHALL remain on the pre-join surface until approval, denial,
  or expiration is received

## Requirement: ActiveCallFragment

The `ActiveCallFragment` SHALL display a LiveKit-backed active video call UI.

### Scenario: ActiveCall UI elements

- **WHEN** ActiveCallFragment is displayed
- **THEN** it SHALL show:
    - A RecyclerView-based participant video grid
    - A draggable self-view overlay sized for picture-in-picture style preview
    - A top bar with call timer and connection quality indicator
    - Floating call controls for microphone, camera, chat, screen-share
      placeholder, and end call
    - Entry points for participants and chat surfaces

### Scenario: Dynamic video tiles reflect participant state

- **WHEN** participant or track state changes during the room session
- **THEN** the grid SHALL update tile count and span layout dynamically
- **THEN** each tile SHALL support participant name overlay, camera-off
  placeholder, muted-mic badge, and active-speaker border state

### Scenario: Controls auto-hide and can be restored

- **WHEN** the call controls are shown during the active call
- **THEN** they SHALL auto-hide after 3 seconds of inactivity
- **THEN** a tap on the call surface SHALL show the controls again

### Scenario: End call action

- **WHEN** user taps the end call button
- **THEN** the system SHALL disconnect from the LiveKit room before finishing
  `VideoCallActivity`
- **THEN** user returns to previous app/task (MainActivity or WelcomeActivity)

## Requirement: CallViewModel

A shared `CallViewModel` SHALL manage backend join and LiveKit room state across
call fragments.

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
    - room connection state (`DISCONNECTED`, `CONNECTING`, `CONNECTED`,
      `RECONNECTING`, `FAILED`)
    - a LiveData participant collection for visible room participants
    - local and remote video-track state needed by the active call UI

### Scenario: Room connection methods coordinate LiveKit session control

- **WHEN** the app has a valid LiveKit token for the current meeting
- **THEN** `CallViewModel` SHALL provide `connectToRoom(url, token)` to start
  the room connection
- **THEN** `toggleLocalMic()` and `toggleLocalCamera()` SHALL update both UI
  state and LiveKit local participant media state
- **THEN** `endCall()` SHALL disconnect from the LiveKit room and stop call
  timers or related resources
