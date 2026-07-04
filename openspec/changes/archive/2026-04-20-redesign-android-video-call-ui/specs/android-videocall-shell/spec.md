# ADDED Requirements

## Requirement: Hosts can update meeting settings during an active call

The Android active-call flow SHALL allow the meeting host to review and replace
meeting settings during a LIVE meeting without leaving the call.

### Scenario: Host can open in-meeting settings

- **WHEN** the current participant is the meeting host and opens the meeting
  actions sheet during an active call
- **THEN** the sheet SHALL show a Settings action
- **THEN** selecting that action SHALL open a meeting settings bottom sheet
  prefilled with the current meeting settings values

### Scenario: Non-hosts do not receive in-meeting settings access

- **WHEN** the current participant is not the meeting host
- **THEN** the meeting actions sheet SHALL NOT show the Settings action
- **THEN** the user SHALL remain able to access the other non-host actions from
  the same sheet

### Scenario: Successful in-meeting settings update refreshes call state

- **WHEN** the host submits valid meeting-settings changes from the in-meeting
  settings sheet
- **THEN** the Android client SHALL call the existing
  `PUT /api/v1/meetings/{id}/settings` flow
- **THEN** the sheet SHALL dismiss after a successful response
- **THEN** `CallViewModel` SHALL publish the updated meeting settings state
  without ending the call

### Scenario: Failed in-meeting settings update preserves the call session

- **WHEN** the meeting-settings update request fails
- **THEN** the active call SHALL remain connected
- **THEN** the settings sheet SHALL stay recoverable for retry or dismissal
- **THEN** the UI SHALL show localized error feedback

# MODIFIED Requirements

## Requirement: ActiveCallFragment

The `ActiveCallFragment` SHALL display a LiveKit-backed active video call UI
with compact controls, layout switching, and host-aware action surfaces.

### Scenario: ActiveCall UI elements

- **WHEN** `ActiveCallFragment` is displayed
- **THEN** it SHALL show:
    - A RecyclerView-based participant video grid
    - A draggable self-view overlay sized for picture-in-picture style preview
    - A top bar with call timer, connection quality indicator, participant
      count, and a layout-switch entry point
    - Floating call controls for microphone, camera, more-actions, and end call
    - Secondary entry points for chat, participants, screen sharing, and
      host-only settings through the meeting actions surface

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

A shared `CallViewModel` SHALL manage backend join state, LiveKit room state,
selected layout, and meeting-settings state across call fragments.

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
    - `currentLayout` for the selected `VideoLayout`
    - `meetingSettings` for the latest editable meeting settings snapshot
    - `isHost` for host-only UI branching

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

## Requirement: Participants BottomSheet

The `ParticipantsBottomSheet` SHALL display participant list over the active
call.

### Scenario: Show participants

- **WHEN** user selects the Participants action from the active call meeting
  actions surface
- **THEN** `ParticipantsBottomSheet` is shown as modal bottom sheet
- **THEN** it displays list of participants (placeholder data for now)

### Scenario: Dismiss participants

- **WHEN** user swipes down or taps outside `ParticipantsBottomSheet`
- **THEN** the bottom sheet dismisses
- **THEN** `ActiveCallFragment` remains visible

## Requirement: MeetingChat BottomSheet

The `MeetingChatBottomSheet` SHALL display chat over the active call.

### Scenario: Show chat

- **WHEN** user selects the Chat action from the active call meeting actions
  surface
- **THEN** `MeetingChatBottomSheet` is shown as modal bottom sheet
- **THEN** it displays chat messages (placeholder data for now)
- **THEN** it has a message input field

### Scenario: Mini video removal

- **WHEN** `MeetingChatBottomSheet` is displayed
- **THEN** it SHALL NOT contain a mini video preview
- **THEN** the old `cardMiniVideo` from `activity_meeting_chat.xml` SHALL be
  removed
