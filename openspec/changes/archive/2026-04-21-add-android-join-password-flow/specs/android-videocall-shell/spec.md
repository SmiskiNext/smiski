# MODIFIED Requirements

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

## Requirement: CallViewModel

A shared `CallViewModel` SHALL manage backend join, password-gating lookup
state, and LiveKit room state across call fragments.

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
