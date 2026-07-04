# ADDED Requirements

## Requirement: Host waiting room SSE session lifecycle

The Android app SHALL establish and manage a host-scoped meeting event SSE
session during active calls when waiting room is enabled.

### Scenario: Host enters active call with waiting room enabled

- **WHEN** a host user enters `ActiveCallFragment` for a meeting where waiting
  room is enabled
- **THEN** the system SHALL connect to `GET /api/v1/meetings/{id}/events`
- **THEN** the system SHALL route SSE events through `CallViewModel` state
  updates

### Scenario: Host enters active call without waiting room capability

- **WHEN** waiting room is disabled for the active meeting or the current user
  is not host
- **THEN** the system SHALL NOT open host meeting SSE subscription
- **THEN** waiting-room badge state SHALL remain hidden from host-only action
  surfaces

### Scenario: Host leaves or ends call

- **WHEN** the active call is ended or the host leaves the call flow
- **THEN** the system SHALL close any host meeting SSE connection
- **THEN** pending reconnect attempts SHALL be cancelled

## Requirement: Host waiting room event processing

The Android app SHALL process host meeting SSE events into pending-join-request
state updates.

### Scenario: Join request created event updates pending list

- **WHEN** SSE receives `join_request_created` with
  `{requestId, meetingId, displayName}`
- **THEN** the request SHALL be added to local pending waiting-room state
- **THEN** pending badge count SHALL increase by one if the request was not
  already present

### Scenario: Join request expired event updates pending list

- **WHEN** SSE receives `join_request_expired` with `{requestId, status}`
- **THEN** the matching request SHALL be removed from local pending waiting-room
  state
- **THEN** pending badge count SHALL decrease accordingly without going below
  zero

### Scenario: Participant kicked event is informational

- **WHEN** SSE receives `participant_kicked` with
  `{meetingId, kickedUserId, displayName}`
- **THEN** the app SHALL record or expose the event as informational only
- **THEN** pending waiting-room list and badge SHALL remain unchanged

## Requirement: Host waiting room reconnection and consistency

The Android app SHALL recover from host SSE disconnects using exponential
backoff and post-reconnect synchronization.

### Scenario: Reconnect delay progression is bounded exponential backoff

- **WHEN** host meeting SSE disconnects unexpectedly
- **THEN** reconnect attempts SHALL be scheduled at 1s, 2s, 4s and continue
  doubling
- **THEN** reconnect delay SHALL be capped at 30 seconds

### Scenario: Pending requests are synchronized after reconnect

- **WHEN** a reconnect succeeds after any disconnect window
- **THEN** the app SHALL call `GET /api/v1/meetings/{id}/joinRequests`
- **THEN** local pending state and badge count SHALL be replaced with the
  synchronized list result

## Requirement: Host waiting room moderation APIs

The Android app SHALL support host moderation actions for pending join requests
via generated API interfaces.

### Scenario: List pending requests for sheet content

- **WHEN** waiting-room data is loaded for an active meeting
- **THEN** the app SHALL fetch pending items from
  `GET /api/v1/meetings/{id}/joinRequests`
- **THEN** the viewmodel SHALL expose loading, success, empty, and error states
  for bottom-sheet rendering

### Scenario: Approve one pending request

- **WHEN** host selects approve for a pending request item
- **THEN** the app SHALL call
  `POST /api/v1/meetings/{id}/joinRequests/{requestId}:approve`
- **THEN** on success the approved request SHALL be removed from pending state

### Scenario: Deny one pending request

- **WHEN** host selects deny for a pending request item
- **THEN** the app SHALL call
  `POST /api/v1/meetings/{id}/joinRequests/{requestId}:deny`
- **THEN** on success the denied request SHALL be removed from pending state

### Scenario: Approve all pending requests

- **WHEN** host selects approve all from waiting-room sheet
- **THEN** the app SHALL call
  `POST /api/v1/meetings/{id}/joinRequests:approveAll`
- **THEN** on success pending list SHALL become empty and badge count SHALL be
  zero

### Scenario: Moderation API failure feedback

- **WHEN** any waiting-room moderation API call fails
- **THEN** the app SHALL keep existing pending items unchanged
- **THEN** the UI SHALL show snackbar-based error feedback with retry paths
  where applicable

## Requirement: Host waiting room UI surfaces

The Android app SHALL provide host-only waiting-room controls in active call
toolbar and bottom-sheet UI.

### Scenario: Toolbar waiting-room action visibility

- **WHEN** `ActiveCallFragment` is rendered for a host in waiting-room-enabled
  meeting
- **THEN** a waiting-room icon action SHALL be visible in the toolbar
- **THEN** non-hosts SHALL NOT see the waiting-room action

### Scenario: Badge displays pending count

- **WHEN** pending waiting-room state changes
- **THEN** the toolbar waiting-room action SHALL display the current pending
  request badge count
- **THEN** badge visibility SHALL hide when count is zero

### Scenario: Bottom sheet renders list states

- **WHEN** host opens `WaitingRoomBottomSheet`
- **THEN** the sheet SHALL show header with title and close action
- **THEN** it SHALL render one of loading, error-with-retry, empty, or
  pending-items states
- **THEN** pending-item rows SHALL display display name, requested time, and
  approve or deny actions
- **THEN** an approve-all action SHALL be available when list has items
