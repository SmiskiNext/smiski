## ADDED Requirements

### Requirement: Web meeting toolbar exposes a screen-share control gated by LiveKit permissions

The web meeting room SHALL render a screen-share toggle control in the floating
meeting toolbar that mirrors the local participant's LiveKit publish permissions
and the current single-publisher state.

#### Scenario: Host always sees screen-share control as available

- **WHEN** a participant joined the meeting as HOST
- **THEN** the toolbar SHALL display the screen-share control as available
- **THEN** clicking the control SHALL invoke
  `localParticipant.setScreenShareEnabled(true)` in the LiveKit client SDK

#### Scenario: Participant sees screen-share control only when allowed

- **WHEN** a participant joined the meeting with role PARTICIPANT
- **AND** the local participant's LiveKit permissions list `screen_share` in the
  publishable sources
- **THEN** the toolbar SHALL display the screen-share control as available

#### Scenario: Participant control hides when screen sharing is revoked

- **WHEN** a PARTICIPANT's LiveKit permissions stop listing `screen_share` among
  publishable sources
- **THEN** the toolbar SHALL hide the screen-share control or render it as
  permanently unavailable
- **THEN** if the local participant was actively sharing, the client SHALL stop
  publishing the local screen-share track

#### Scenario: Guest never sees screen-share control

- **WHEN** a participant joined the meeting as GUEST
- **THEN** the toolbar SHALL NOT display the screen-share control

### Requirement: Web client enforces a single active screen sharer

The web client SHALL allow only one screen-share publisher to be visibly
initiated at a time and SHALL communicate the active sharer to the local user.

#### Scenario: Another participant is already sharing

- **WHEN** a remote participant has an active publication for
  `Track.Source.ScreenShare`
- **AND** the local participant is not the active sharer
- **THEN** the screen-share control SHALL be disabled
- **THEN** the control SHALL render a tooltip or accessible label that names the
  active sharer

#### Scenario: Local participant is sharing

- **WHEN** the local participant has an active publication for
  `Track.Source.ScreenShare`
- **THEN** the screen-share control SHALL render in an active state with a "stop
  sharing" affordance
- **THEN** clicking the control SHALL invoke
  `localParticipant.setScreenShareEnabled(false)`

### Requirement: Web meeting room renders remote screen-share tracks with auto-spotlight

The web client SHALL render a remote `screen_share` publication as a participant
tile and SHALL auto-promote that tile to the spotlight position.

#### Scenario: Remote screen-share track promoted to spotlight

- **WHEN** any remote participant publishes a `Track.Source.ScreenShare`
  publication
- **THEN** the meeting layout SHALL pin the sharer's identity for the
  spotlight/sidebar layouts
- **THEN** the participant tile for that identity SHALL display the screen-share
  video track instead of the camera track

#### Scenario: Local screen share appears in the participant grid

- **WHEN** the local participant publishes screen share
- **THEN** the local screen-share track SHALL be visible to the local user
  alongside their camera self-view

#### Scenario: Spotlight reverts when sharing ends

- **WHEN** the active screen-share publication is removed (sharer stops or loses
  publish rights)
- **THEN** the layout SHALL drop the auto-pin and return to its prior pinning
  state

### Requirement: Web client surfaces screen-share start failures without breaking the meeting

The web client SHALL handle browser-side screen capture failures gracefully.

#### Scenario: User denies the browser screen-share picker

- **WHEN** the browser `getDisplayMedia` prompt is dismissed or denied
- **THEN** the toolbar control SHALL return to its idle state
- **THEN** no screen-share publication SHALL be started

#### Scenario: Screen-share track ends from outside the app

- **WHEN** the OS-side stop button ends the screen-share source while the user
  is still in the meeting
- **THEN** the local publication SHALL be unpublished
- **THEN** the toolbar control SHALL return to the idle state
