# Tasks

## 1. Domain Type and Mock Removal

- [x] 1.1 Define `ChatMessage` domain type in `src/types/chat.ts` (fields: `id`,
      `seqNum`, `roomId`, `senderId`, `senderName`, `content`,
      `type:     "TEXT" | "SYSTEM"`, `createdAt`)
- [x] 1.2 Delete `src/lib/mock-data/messages.ts`
- [x] 1.3 Remove all imports of `MeetingMessage` and `INITIAL_MEETING_MESSAGES`
      from `index.tsx`, `chat.tsx`, and `sidebar.tsx` ← (verify: project
      type-checks and builds without errors after deletion)

## 2. i18n Keys

- [x] 2.1 Add keys `chatLoading`, `chatEmpty`, `chatError`, `chatRetry`,
      `chatSendError`, `systemMessageJoined`, `systemMessageLeft` under
      `meetingRoom` in `src/i18n/messages/en.json`
- [x] 2.2 Add matching Vietnamese translations for all seven keys under
      `meetingRoom` in `src/i18n/messages/vi.json` ← (verify: both locale files
      parse as valid JSON and all keys are present under meetingRoom)

## 3. `useMeetingChat` Hook

- [x] 3.1 Create `src/hooks/use-meeting-chat.ts` with signature
      `useMeetingChat(meetingId: string, userId: string, isChatVisible: boolean)`
- [x] 3.2 Implement `ChatMessage` merge helper: dedup by `id`, sort ascending by
      `seqNum`
- [x] 3.3 Implement `loadHistory()`: call `getMessages` SDK, merge result into
      state; track `historyLoaded` flag to prevent duplicate calls
- [x] 3.4 Implement `send(content: string, senderName: string)`: call
      `sendMessage` SDK, merge returned message on success, emit send-error
      event on failure
- [x] 3.5 Register `RoomEvent.DataReceived` listener on the LiveKit room object
      via `useRoomContext()`; parse UTF-8 JSON payload into `ChatMessage`; merge
      on success; silently drop with `console.debug` on parse failure
- [x] 3.6 Increment `unreadCount` when a data channel message arrives and
      `isChatVisible` is `false`; reset `unreadCount` to zero when
      `isChatVisible` transitions to `true`
- [x] 3.7 Remove the `RoomEvent.DataReceived` listener in the hook's cleanup
      function ← (verify: hook exports messages, loading, error, unreadCount,
      loadHistory, send; listener is removed on unmount; duplicate messages are
      not added)

## 4. `MeetingChat` Component Update

- [x] 4.1 Update props to accept `messages: ChatMessage[]`, `loading: boolean`,
      `error: boolean`, `onRetry: () => void`,
      `onSend: (content: string) =>     void`, `sendError: boolean`
- [x] 4.2 Render loading state using shadcn spinner and localised `chatLoading`
      key while `loading` is true
- [x] 4.3 Render empty state with a Lucide icon and localised `chatEmpty` key
      when `messages` is empty and `loading` is false
- [x] 4.4 Render error state with localised `chatError` text and a retry button
      (`chatRetry`) when `error` is true
- [x] 4.5 Implement outgoing message row: right-aligned, primary background, no
      sender name (condition: `senderId === userId`)
- [x] 4.6 Implement incoming message row: left-aligned, `senderName` displayed
      above content
- [x] 4.7 Implement system message row: centered, italic, muted color; use
      `systemMessageJoined` or `systemMessageLeft` i18n key based on content
      heuristic or type field
- [x] 4.8 Show brief toast using `chatSendError` when `sendError` is true
- [x] 4.9 Attach a scroll-to-bottom effect that fires when `messages.length`
      changes ← (verify: all three message render types display correctly;
      loading/empty/error states render correctly; auto-scroll works on new
      message)

## 5. `MeetingSidebar` Component Update

- [x] 5.1 Update `messages` prop type from `MeetingMessage[]` to `ChatMessage[]`
- [x] 5.2 Pass `loading`, `error`, `onRetry`, `sendError` props through to
      `MeetingChat`
- [x] 5.3 Call `onLoadHistory()` prop when the chat tab becomes active for the
      first time ← (verify: sidebar correctly forwards all new props; history
      load is triggered on first chat tab open)

## 6. `MeetingToolbar` Component Update

- [x] 6.1 Add `unreadCount: number` prop to `MeetingToolbar`
- [x] 6.2 Render a badge or dot on the chat toggle button when `unreadCount > 0`
      using shadcn `Badge` or a Tailwind dot overlay ← (verify: badge appears
      when unreadCount > 0 and disappears when count resets to 0)

## 7. `MeetingRoomContent` Wiring

- [x] 7.1 Call `useMeetingChat(meetingId, userId, isChatVisible)` in
      `MeetingRoomContent` (`index.tsx`)
- [x] 7.2 Remove all references to `INITIAL_MEETING_MESSAGES`, mock
      `handleSendMessage`, and the old `MeetingMessage` type
- [x] 7.3 Pass `messages`, `loading`, `error`, `onRetry`, `send`, `sendError`,
      `unreadCount`, `onLoadHistory` from the hook down to `MeetingSidebar` and
      `MeetingToolbar`
- [x] 7.4 Derive `isChatVisible` from the sidebar open state and active tab, and
      pass it into `useMeetingChat` ← (verify: full end-to-end flow works —
      history loads on chat open, messages send and appear, data channel
      messages arrive in real time, unread badge increments and resets)
