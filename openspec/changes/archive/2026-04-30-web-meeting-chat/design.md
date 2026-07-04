# Context

The web meeting room at `frontends/web/src/components/meeting/` currently
renders chat using hardcoded mock data. The `chat-management` backend service is
fully operational: REST endpoints for history and send are available, and the
server broadcasts new messages to all LiveKit room participants as reliable data
packets. Three generated SDK functions (`sendMessage`, `getMessages`, `getRoom`)
exist but are unused. The `livekit-client` and `@livekit/components-react`
packages are already installed and the meeting room is wrapped in
`<LiveKitRoom>`, making the room object accessible via `useRoomContext()`.

## Goals / Non-Goals

**Goals:**

- Replace all mock chat data with real backend and LiveKit data channel
  integration
- Encapsulate all chat logic (state, HTTP calls, data channel subscription) in a
  single custom hook `useMeetingChat`
- Render three distinct message types: outgoing, incoming, and system
- Display loading, empty, and error UI states in the chat panel
- Show an unread badge on the toolbar chat button when the sidebar is closed and
  new messages arrive

**Non-Goals:**

- Scroll-to-load older messages (cursor pagination beyond initial load)
- Message attachments, reply threading, typing indicators, editing, or deletion
- Any changes to the backend `chat-management` service
- Changes to the Android client

## Decisions

### Use `RoomEvent.DataReceived` directly instead of `useDataChannel`

The backend broadcasts chat messages without a topic (topic is undefined or
empty). The `useDataChannel` hook from `@livekit/components-react` filters by a
specific topic string and would never fire. Subscribing to
`RoomEvent.DataReceived` on the raw LiveKit `Room` object (obtained via
`useRoomContext()`) is the correct approach and matches the Android
implementation pattern.

Alternative considered: wrapping `useDataChannel` with an empty topic. Rejected
because the hook signature does not support topic-less subscription.

### Single custom hook owns all chat state

All loading, error, messages, unread count, and side-effecting operations are
kept in `useMeetingChat(meetingId, userId)`. Components remain presentational
and receive only the data they render.

Alternative considered: co-locating state in `MeetingRoomContent` directly.
Rejected because a dedicated hook isolates LiveKit event listener
registration/cleanup in one place, is independently testable, and prevents
component bloat.

### Domain type derived from generated SDK response

The `MeetingMessage` mock type is replaced by a `ChatMessage` domain type whose
fields mirror `ChatManagementChatMessageResponse` from the generated SDK. This
avoids a separate mapping layer while keeping component code readable.

### History loaded when chat tab first becomes active

`loadHistory` is called once when the user opens the chat tab (sidebar reveals
chat panel). It is not re-called on subsequent tab switches. Real-time messages
from the data channel keep the list current between tab switches.

Alternative considered: loading history on room join. Rejected to avoid
unnecessary API calls for users who never open chat.

### Message merging: dedup by `id`, sort ascending by `seqNum`

Both the HTTP history response and data channel payloads feed into a single
merge function. A `Map<string, ChatMessage>` keyed by `id` enforces dedup; the
final array is sorted by `seqNum` ascending. This matches the Android behavior
exactly.

### Send error: toast notification, input preserved

On send failure the input is not cleared and a toast is shown. This allows the
user to retry without retyping their message, consistent with the Android
pattern.

### Unread count: incremented on data channel messages when sidebar is closed

The hook receives an `isChatVisible` boolean. When `false` and a data channel
message arrives, `unreadCount` increments. The count resets to zero when the
chat tab is opened.

## Risks / Trade-offs

- **Malformed data channel payloads** → Silently dropped with a `console.debug`
  call. The LiveKit room remains stable.
- **Room not ACTIVE on send** → The backend returns an HTTP error. The hook
  surfaces this as a send failure (toast), input preserved. No client-side
  pre-validation of room state to avoid desync.
- **History loads only once** → If the user joins mid-meeting and misses
  messages delivered before their join, they see only the history returned by
  the API at join time plus real-time messages. This is acceptable and matches
  Android behaviour.
- **No pagination** → For very long meetings the initial `getMessages` call will
  return a bounded set (server default page size). Messages beyond that window
  are not loaded. Acceptable as a V1 scoped match with Android.
