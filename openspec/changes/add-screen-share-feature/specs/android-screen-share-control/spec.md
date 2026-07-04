## ADDED Requirements

### Requirement: Android in-call UI exposes a screen-share control gated by LiveKit permissions

The Android in-call screen SHALL provide a screen-share control whose visibility
and enabled state mirror the local participant's LiveKit publish permissions and
the current single-publisher state.

#### Scenario: Host can always start screen sharing

- **WHEN** the local participant is HOST
- **THEN** the in-call action sheet SHALL render the screen-share row as enabled
- **THEN** tapping the row SHALL initiate the screen-share consent flow

#### Scenario: Participant can share when allowed by settings

- **WHEN** the local participant is PARTICIPANT
- **AND** the local LiveKit permissions list `screen_share` among publishable
  sources
- **THEN** the in-call action sheet SHALL render the screen-share row as enabled

#### Scenario: Participant control disables when permission is revoked

- **WHEN** the local LiveKit permissions stop listing `screen_share`
- **THEN** the in-call action sheet SHALL hide the screen-share row or render it
  disabled
- **THEN** if a local screen-share publication is in flight, the client SHALL
  stop publishing it

#### Scenario: Guest never sees screen-share control

- **WHEN** the local participant joined as GUEST
- **THEN** the in-call action sheet SHALL NOT display the screen-share row

### Requirement: Android client enforces a single active screen sharer

The Android client SHALL allow only one screen-share publisher to be visibly
initiated at a time.

#### Scenario: Another participant is already sharing

- **WHEN** any remote participant has an active publication of source screen
  share
- **AND** the local participant is not the active sharer
- **THEN** the screen-share row SHALL be disabled
- **THEN** the row's secondary text or accessibility label SHALL identify the
  active sharer by display name

#### Scenario: Local participant is sharing

- **WHEN** the local participant has an active screen-share publication
- **THEN** the screen-share row SHALL render in a stop-sharing state
- **THEN** tapping the row SHALL stop the local screen-share publication

### Requirement: Android client requests MediaProjection consent and runs a foreground service of type mediaProjection

The Android client SHALL obtain `MediaProjection` consent through the system
dialog and SHALL run a foreground service of type `mediaProjection` for the
duration of any local screen-share publication.

#### Scenario: User starts screen share

- **WHEN** the user taps the screen-share row in the action sheet
- **THEN** the client SHALL launch the system intent created by
  `MediaProjectionManager.createScreenCaptureIntent()` via an
  `ActivityResultLauncher`
- **AND** SHALL start the foreground `ScreenCaptureService` before the LiveKit
  SDK begins capture
- **AND** SHALL pass the consent `Intent` to the LiveKit publish call

#### Scenario: User denies the consent prompt

- **WHEN** the user dismisses or denies the screen-capture consent prompt
- **THEN** the client SHALL NOT start the foreground service
- **THEN** the client SHALL NOT call the LiveKit publish API
- **THEN** the action sheet row SHALL return to its idle state

#### Scenario: Foreground service stops when sharing ends

- **WHEN** the local screen-share publication ends (user-initiated stop, OS
  stop, or revoked permission)
- **THEN** the client SHALL stop the foreground `ScreenCaptureService`
- **THEN** the system notification posted by the service SHALL be dismissed

#### Scenario: Manifest declares foreground service permissions and type

- **WHEN** the application is installed on a device running Android 10+
- **THEN** the manifest SHALL declare `android.permission.FOREGROUND_SERVICE`
  and `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`
- **THEN** the manifest SHALL declare the screen-capture service with
  `android:foregroundServiceType="mediaProjection"`

### Requirement: Android in-call grid renders remote screen-share tracks with auto-spotlight

The Android in-call grid SHALL render a remote screen-share publication as a
participant tile and SHALL auto-promote that tile to the spotlight position.

#### Scenario: Remote screen-share track promoted

- **WHEN** any remote participant has an active screen-share publication
- **THEN** the in-call grid SHALL render that participant's screen-share track
  as the spotlighted (large) tile
- **THEN** other participants SHALL render as thumbnails

#### Scenario: Local screen share appears alongside camera tile

- **WHEN** the local participant publishes screen share
- **THEN** the in-call grid SHALL surface the local screen-share track alongside
  the existing self-camera preview

#### Scenario: Spotlight ends when sharing stops

- **WHEN** the active screen-share publication ends
- **THEN** the in-call grid SHALL revert to the previously selected layout mode
  without the auto-promotion
