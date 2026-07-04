# ADDED Requirements

## Requirement: Web client loads message history when chat panel is opened

The web meeting room SHALL load chat message history from the backend REST API
the first time the user opens the chat tab during an active meeting session.

### Scenario: History loads successfully

- **WHEN** the user opens the chat tab for the first time in an active meeting
- **THEN** `useMeetingChat` SHALL call `getMessages(roomId)` via the generated
  SDK
- **THEN** the returned messages SHALL be merged into the message state, deduped
  by `id`, and sorted ascending by `seqNum`
- **THEN** `MeetingChat` SHALL transition from loading state to content state

### Scenario: History returns no messages

- **WHEN** `getMessages` returns an empty list
- **THEN** `useMeetingChat` SHALL set an explicit empty state
- **THEN** `MeetingChat` SHALL render an empty state with an icon and localised
  text

### Scenario: History load fails

- **WHEN** `getMessages` fails due to a network or server error
- **THEN** `useMeetingChat` SHALL set an error state
- **THEN** `MeetingChat` SHALL render an error state with a localised retry
  button
- **THEN** tapping retry SHALL re-invoke `getMessages` without requiring the
  user to close and reopen the panel

### Scenario: Chat tab opened again after initial load

- **WHEN** the user switches away from the chat tab and back
- **THEN** `useMeetingChat` SHALL NOT issue a second `getMessages` call
- **THEN** the previously loaded messages SHALL be displayed immediately

## Requirement: Web client sends text messages using the generated SDK

The web chat panel SHALL send user-authored text messages through the
`sendMessage` SDK function and update the message list on success.

### Scenario: Send succeeds

- **WHEN** the user submits a non-empty message (up to 4000 characters)
- **THEN** `useMeetingChat` SHALL call
  `sendMessage(roomId, { content, type: "TEXT" })`
- **THEN** the returned message object SHALL be merged into local message state
- **THEN** the text input SHALL be cleared

### Scenario: Send fails

- **WHEN** the `sendMessage` SDK call returns an error
- **THEN** `useMeetingChat` SHALL expose a send-error event
- **THEN** `MeetingChat` SHALL display a brief error toast using the localised
  `chatSendError` key
- **THEN** the text input SHALL retain its current content so the user can retry

### Scenario: Send is blocked on empty input

- **WHEN** the text input is empty or contains only whitespace
- **THEN** the send action SHALL be disabled and no SDK call SHALL be made

## Requirement: Web client receives real-time messages from the LiveKit data channel

The web meeting room SHALL listen to `RoomEvent.DataReceived` on the LiveKit
`Room` object and merge parsed chat messages into the live timeline without
requiring a page refresh.

### Scenario: Valid chat payload is received

- **WHEN** the LiveKit room emits a `DataReceived` event with a UTF-8 JSON
  payload containing `id`, `seqNum`, `roomId`, `senderId`, `senderName`,
  `content`, `type`, and `createdAt`
- **THEN** `useMeetingChat` SHALL parse the payload into a `ChatMessage` domain
  object
- **THEN** the message SHALL be merged into the messages state with dedup by
  `id` and sorted ascending by `seqNum`
- **THEN** no manual refresh SHALL be required to see the new message

### Scenario: Duplicate real-time message is received

- **WHEN** a data channel message has the same `id` as an already-loaded message
- **THEN** `useMeetingChat` SHALL discard the duplicate
- **THEN** the timeline SHALL not render duplicate rows

### Scenario: Malformed data channel payload is received

- **WHEN** the received binary payload cannot be parsed as valid JSON or is
  missing required fields
- **THEN** `useMeetingChat` SHALL silently discard the payload
- **THEN** a debug-level log entry SHALL be emitted
- **THEN** the meeting room and chat UI SHALL remain fully stable

### Scenario: Data channel listener is cleaned up on unmount

- **WHEN** `MeetingRoomContent` unmounts (user leaves meeting)
- **THEN** the `RoomEvent.DataReceived` listener registered by `useMeetingChat`
  SHALL be removed from the LiveKit room object

## Requirement: Chat UI renders outgoing, incoming, and system message types

`MeetingChat` SHALL render each message with a distinct visual style based on
its type and the current user's identity.

### Scenario: Outgoing message is rendered

- **WHEN** a message's `senderId` matches the current user's `userId`
- **THEN** `MeetingChat` SHALL render the message right-aligned with primary
  color styling and SHALL NOT display a sender name

### Scenario: Incoming message is rendered

- **WHEN** a message's `senderId` does not match the current user's `userId` and
  `type` is `TEXT`
- **THEN** `MeetingChat` SHALL render the message left-aligned and SHALL display
  `senderName` above the message content

### Scenario: System message is rendered

- **WHEN** a message's `type` is `SYSTEM`
- **THEN** `MeetingChat` SHALL render the message centered with italic muted
  styling using the appropriate localised key (`systemMessageJoined` or
  `systemMessageLeft`)

## Requirement: Chat panel auto-scrolls to the latest message

`MeetingChat` SHALL keep the most recent message visible as the list grows.

### Scenario: New message arrives while panel is open

- **WHEN** a new message is added to the messages array (via HTTP result or data
  channel)
- **THEN** `MeetingChat` SHALL scroll to the bottom of the message list

## Requirement: Toolbar displays an unread badge when sidebar is closed

The meeting toolbar SHALL show a visual indicator on the chat button when new
messages arrive while the sidebar is not showing the chat panel.

### Scenario: New message arrives while sidebar is closed

- **WHEN** a data channel message is received and the sidebar is not open on the
  chat tab
- **THEN** `useMeetingChat` SHALL increment the unread count
- **THEN** `MeetingToolbar` SHALL render a badge or dot on the chat toggle
  button

### Scenario: User opens the chat tab

- **WHEN** the user opens the sidebar on the chat tab
- **THEN** `useMeetingChat` SHALL reset the unread count to zero
- **THEN** `MeetingToolbar` SHALL remove the badge

## Requirement: Mock chat data is fully removed

The web client SHALL no longer reference any hardcoded mock chat data.

### Scenario: Application builds without mock messages module

- **WHEN** `messages.ts` is deleted from `src/lib/mock-data/`
- **THEN** all TypeScript imports of `MeetingMessage`,
  `INITIAL_MEETING_MESSAGES`, or any symbol from that file SHALL be replaced or
  removed
- **THEN** the project SHALL build without errors or type warnings

## Requirement: Chat i18n keys are defined for all supported locales

All user-visible chat strings SHALL be defined under the `meetingRoom` namespace
in both `en.json` and `vi.json`.

### Scenario: All required keys exist

- **WHEN** the i18n message files are loaded
- **THEN** the following keys SHALL exist under `meetingRoom` in both files:
  `chatLoading`, `chatEmpty`, `chatError`, `chatRetry`, `chatSendError`,
  `systemMessageJoined`, `systemMessageLeft`
