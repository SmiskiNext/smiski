# ADDED Requirements

## Requirement: In-meeting chat is available only during active call sessions

The Android app SHALL allow users to open and use in-meeting chat only while an
active meeting session is in progress.

### Scenario: Active call can open chat

- **WHEN** `ActiveCallFragment` is connected to an active meeting
- **THEN** the user SHALL be able to open `MeetingChatBottomSheet`
- **THEN** the chat flow SHALL use the current meeting ID as `roomId`

### Scenario: Inactive meeting cannot use chat

- **WHEN** the meeting is not active, has ended, or has been cancelled
- **THEN** the Android chat flow SHALL NOT start message load/send operations
- **THEN** the UI SHALL show a recoverable unavailable/empty state instead of
  chat content

## Requirement: Android client loads message history from existing chat API

The Android chat data layer SHALL load message history using the existing
generated `ChatApi` contract and map responses into domain `ChatMessage` models.

### Scenario: History load succeeds

- **WHEN** `MeetingChatViewModel` requests history for an active room
- **THEN** repository SHALL call `getMessages(roomId, size, beforeSeqNum)`
- **THEN** mapped messages SHALL be published to UI in deterministic order by
  sequence number

### Scenario: History load returns no messages

- **WHEN** the API returns an empty message list for the room
- **THEN** ViewModel SHALL publish an explicit empty-content state
- **THEN** the bottom sheet SHALL render the empty state without failing

### Scenario: History load fails

- **WHEN** history request fails due to network or server error
- **THEN** ViewModel SHALL publish an error state with retry capability
- **THEN** existing chat content state SHALL remain recoverable on subsequent
  reload

## Requirement: Android client sends text messages using existing chat API

The Android chat flow SHALL send user text messages through
`ChatApi.sendMessage(roomId, request)` and update UI send status.

### Scenario: Send succeeds

- **WHEN** the user submits a non-empty text message
- **THEN** ViewModel SHALL invoke the send-message use case with current
  `roomId`
- **THEN** the UI SHALL clear input and reflect successful send in the timeline

### Scenario: Send fails

- **WHEN** the send request fails
- **THEN** ViewModel SHALL expose a send failure event/state
- **THEN** the UI SHALL keep user context and allow retry without losing loaded
  history

## Requirement: Android client receives real-time chat messages from LiveKit data channel

The Android call integration SHALL consume LiveKit reliable data packets for
chat and merge parsed messages into the chat timeline.

### Scenario: Valid chat payload is received

- **WHEN** LiveKit emits a reliable data message containing UTF-8 JSON payload
  with `id`, `seqNum`, `senderId`, `senderName`, `content`, `type`, and
  `createdAt`
- **THEN** the client SHALL parse payload into domain `ChatMessage`
- **THEN** ViewModel SHALL append or merge the message into timeline state
  without requiring manual refresh

### Scenario: Duplicate or out-of-order real-time message is received

- **WHEN** a received message duplicates an existing `id` or `seqNum`, or
  arrives out of order
- **THEN** the client SHALL de-duplicate and keep deterministic timeline
  ordering
- **THEN** the UI SHALL avoid duplicate rendered items

### Scenario: Malformed data payload is received

- **WHEN** LiveKit data payload cannot be parsed into the expected chat contract
- **THEN** the client SHALL ignore the malformed payload safely
- **THEN** active call and chat UI SHALL remain stable

## Requirement: Chat UI renders outgoing, incoming, and system message types

`MeetingChatBottomSheet` SHALL render chat messages with distinct row types in
RecyclerView and preserve required system message styling.

### Scenario: Outgoing and incoming messages use distinct layouts

- **WHEN** the timeline contains user-authored and other-user messages
- **THEN** RecyclerView adapter SHALL render outgoing and incoming message item
  layouts separately
- **THEN** each row SHALL display sender and timestamp information according to
  existing UI conventions

### Scenario: System messages are styled as centered gray text

- **WHEN** a message has system `type`
- **THEN** adapter SHALL render a dedicated system-message row
- **THEN** system message text SHALL be centered and gray

## Requirement: Meeting chat ViewModel exposes complete UI states

`MeetingChatViewModel` SHALL manage and expose loading, empty, success/content,
and error states for history + send + receive operations.

### Scenario: Initial load state transitions to content

- **WHEN** chat opens and history request begins
- **THEN** ViewModel SHALL emit loading state
- **THEN** ViewModel SHALL transition to content or empty state after request
  completes

### Scenario: Receive updates while sheet is open

- **WHEN** new chat messages arrive in real time while `MeetingChatBottomSheet`
  is visible
- **THEN** ViewModel SHALL update content state incrementally
- **THEN** RecyclerView SHALL reflect new rows without reinitializing the sheet

## Requirement: Chat implementation includes practical automated tests

The Android chat feature SHALL include focused tests for mapping and state logic
in line with project testing patterns.

### Scenario: Mapper conversion is validated

- **WHEN** chat API or LiveKit payload DTOs are converted
- **THEN** tests SHALL verify required field mapping into domain `ChatMessage`
- **THEN** tests SHALL verify handling for system message types

### Scenario: Repository/use case and ViewModel behavior is validated

- **WHEN** history/send/receive success and failure paths are exercised in tests
- **THEN** repository or use-case tests SHALL verify expected result mapping
- **THEN** ViewModel tests SHALL verify loading, empty, success, and error state
  transitions
