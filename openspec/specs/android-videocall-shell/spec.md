# Purpose

Define the Android video-call shell, task isolation, call navigation, and
pre-call or in-call container requirements.

# ADDED Requirements

## Requirement: VideoCallActivity as Separate Task

The `VideoCallActivity` SHALL run as a separate Android task for video call
isolation.

### Scenario: Manifest configuration

- **WHEN** `VideoCallActivity` is declared in `AndroidManifest.xml`
- **THEN** it SHALL have
  `android:taskAffinity="io.github.phunguy65.zms.videocall"`
- **THEN** it SHALL have `android:launchMode="singleInstance"`
- **THEN** it SHALL have
  `android:configChanges="orientation|screenSize|smallestScreenSize|keyboardHidden"`
- **THEN** it SHALL have `android:supportsPictureInPicture="true"`

### Scenario: Separate recents card

- **WHEN** VideoCallActivity is running
- **THEN** it SHALL appear as a separate card in Android Recents
- **THEN** pressing Back SHALL close only the call, not the main app

### Scenario: Launch from main app meeting flows

- **WHEN** user successfully starts an instant meeting from the dashboard or
  joins a meeting from the main app
- **THEN** system creates Intent to `VideoCallActivity.class`
- **THEN** Intent has flag `FLAG_ACTIVITY_NEW_TASK`
- **THEN** authenticated launches from instant meeting creation SHALL include
  the created meeting short code in `EXTRA_MEETING_CODE`

## Requirement: Camera and Microphone Permissions

The app SHALL declare and request camera and microphone permissions for video
calls.

### Scenario: Manifest permissions

- **WHEN** `AndroidManifest.xml` is loaded
- **THEN** it SHALL declare:
    - `android.permission.CAMERA`
    - `android.permission.RECORD_AUDIO`
    - `android.permission.MODIFY_AUDIO_SETTINGS`

### Scenario: Runtime permission request

- **WHEN** user opens PreJoinFragment
- **THEN** system SHALL check if camera and microphone permissions are granted
- **WHEN** permissions are not granted
- **THEN** system SHALL request permissions before enabling camera preview

## Requirement: VideoCall Navigation Graph

The `VideoCallActivity` SHALL use a navigation graph for call flow.

### Scenario: nav_graph_call structure

- **WHEN** `nav_graph_call.xml` is loaded
- **THEN** it SHALL have `PreJoinFragment` as `startDestination`
- **THEN** it SHALL have `ActiveCallFragment` as a destination
- **THEN** it SHALL have action `action_prejoin_to_activeCall`

### Scenario: Navigation from PreJoin to ActiveCall

- **WHEN** user taps "Join" button on PreJoinFragment
- **THEN** system navigates via `action_prejoin_to_activeCall`
- **THEN** PreJoinFragment is removed from back stack (popUpTo with
  inclusive=true)

## Requirement: PreJoinFragment

The `PreJoinFragment` SHALL handle pre-call setup, password-aware meeting
lookup, and backend room-join orchestration for both guest and authenticated
users.

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
    - Password label and input that are hidden by default
    - Microphone toggle switch
    - Camera toggle switch
    - "Join" button

### Scenario: Validation before initial lookup

- **WHEN** user taps "Join" without meeting code
- **THEN** system SHALL show inline error on meeting code field
- **THEN** it SHALL NOT request meeting lookup or room join

### Scenario: Validation before protected join submission

- **WHEN** the current meeting requires a password and user taps "Join" without
  entering one
- **THEN** system SHALL show inline error on the password field
- **THEN** it SHALL NOT submit the join request until a password is provided

### Scenario: Password section appears after protected lookup

- **WHEN** meeting lookup succeeds and reports `requirePassword=true`
- **THEN** PreJoinFragment SHALL animate the password label and input into view
  with an expand-and-fade transition
- **THEN** the join button SHALL return to an enabled state after the lookup
- **THEN** the password input SHALL request focus after the reveal completes

### Scenario: Meeting lookup loading state is visible

- **WHEN** the app is fetching meeting info by short code before a join request
- **THEN** the join button SHALL be disabled
- **THEN** a spinner and checking label SHALL appear if the lookup remains in
  progress past the configured delay
- **THEN** the loading state SHALL clear when lookup succeeds, fails, or the
  join request phase begins

### Scenario: Meeting code changes invalidate password prompt

- **WHEN** the user changes the meeting code after the password UI is visible
- **THEN** the fragment SHALL clear the password value and related errors
- **THEN** it SHALL treat the next tap on Join as a fresh lookup for the new
  code
- **THEN** it SHALL NOT reuse the previous meeting UUID or password-required
  state

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

### Scenario: Lookup and password errors stay attached to the correct field

- **WHEN** meeting lookup reports not found, join submission fails with invalid
  password, or lookup fails due to network issues
- **THEN** not-found feedback SHALL appear inline on the meeting code field
- **THEN** invalid-password feedback SHALL appear inline on the password field
- **THEN** retryable network lookup failures SHALL be shown through a snackbar
  with a retry action

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

## Requirement: Host leave behavior in active call

The active-call leave flow SHALL branch based on whether the current user is the
meeting host. Non-host users SHALL continue to see the existing single-option
leave confirmation dialog. Host users SHALL see a two-option dialog that
distinguishes between leaving locally and ending the meeting for all
participants.

### Scenario: Non-host taps end-call button

- **WHEN** a non-host user taps the end-call button during an active call
- **THEN** the system SHALL present a confirmation dialog with a single "Leave
  Meeting" action and a cancel option, matching prior behavior

### Scenario: Host taps end-call button

- **WHEN** a host user taps the end-call button during an active call
- **THEN** the system SHALL present a dialog with two primary options: "Leave
  Meeting" (local disconnect only) and "End Meeting for All" (triggers backend
  termination)

### Scenario: Host selects local leave

- **WHEN** a host selects "Leave Meeting" from the host leave dialog
- **THEN** the system SHALL disconnect from the room and finish
  VideoCallActivity, identical to the existing non-host leave path

### Scenario: Host selects end meeting for all

- **WHEN** a host selects "End Meeting for All" from the host leave dialog
- **THEN** the system SHALL initiate the end-meeting backend flow as defined in
  the android-host-meeting-termination capability

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

## Requirement: Guest Flow Entry Point

The guest join flow SHALL launch `VideoCallActivity` directly from
`WelcomeActivity`.

### Scenario: Welcome to VideoCall guest flow

- **WHEN** user taps "Join as Guest" on WelcomeActivity
- **THEN** system creates Intent to `VideoCallActivity.class`
- **THEN** Intent has extra `isGuest=true`
- **THEN** system calls `startActivity(intent)`

### Scenario: JoinGuestActivity deletion

- **WHEN** the migration is complete
- **THEN** `JoinGuestActivity.java` SHALL be deleted
- **THEN** `activity_join_guest.xml` SHALL be deleted
- **THEN** `JoinGuestViewModel.java` SHALL be deleted
