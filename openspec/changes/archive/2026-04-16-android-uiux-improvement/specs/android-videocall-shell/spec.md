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

### Scenario: Launch from MainActivity

- **WHEN** user taps "Join Meeting" or "New Meeting" in MainActivity
- **THEN** system creates Intent to `VideoCallActivity.class`
- **THEN** Intent has flag `FLAG_ACTIVITY_NEW_TASK`

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

The `PreJoinFragment` SHALL handle pre-call setup for both guest and
authenticated users.

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
    - Camera preview (placeholder for now)
    - Meeting code input field
    - Microphone toggle switch
    - Camera toggle switch
    - "Join" button

### Scenario: Validation before join

- **WHEN** user taps "Join" without meeting code
- **THEN** system SHALL show inline error on meeting code field
- **WHEN** guest user taps "Join" without display name
- **THEN** system SHALL show inline error on display name field

## Requirement: ActiveCallFragment

The `ActiveCallFragment` SHALL display the active video call UI.

### Scenario: ActiveCall UI elements

- **WHEN** ActiveCallFragment is displayed
- **THEN** it SHALL show:
    - Video grid area (placeholder for LiveKit)
    - Call controls overlay (mute, camera, end call)
    - Participants button
    - Chat button

### Scenario: End call action

- **WHEN** user taps "End Call" button
- **THEN** system SHALL finish `VideoCallActivity`
- **THEN** user returns to previous app/task (MainActivity or WelcomeActivity)

## Requirement: CallViewModel

A shared `CallViewModel` SHALL manage state across call fragments.

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

## Requirement: Participants BottomSheet

The `ParticipantsBottomSheet` SHALL display participant list over the active
call.

### Scenario: Show participants

- **WHEN** user taps "Participants" button on ActiveCallFragment
- **THEN** `ParticipantsBottomSheet` is shown as modal bottom sheet
- **THEN** it displays list of participants (placeholder data for now)

### Scenario: Dismiss participants

- **WHEN** user swipes down or taps outside ParticipantsBottomSheet
- **THEN** the bottom sheet dismisses
- **THEN** ActiveCallFragment remains visible

## Requirement: MeetingChat BottomSheet

The `MeetingChatBottomSheet` SHALL display chat over the active call.

### Scenario: Show chat

- **WHEN** user taps "Chat" button on ActiveCallFragment
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
