# Why

The web meeting room currently uses hardcoded mock messages for its chat panel,
making the feature non-functional. The chat backend and LiveKit data channel
infrastructure are already in place; the web client needs to connect to them so
participants can communicate in real time during a meeting.

## What Changes

- Delete `src/lib/mock-data/messages.ts` (mock chat data removed entirely)
- Create `src/hooks/use-meeting-chat.ts` — custom hook encapsulating all chat
  state, history loading, message sending, and LiveKit data channel listening
- Replace the `MeetingMessage` mock type with a domain type derived from the
  generated `ChatManagementChatMessageResponse` SDK model
- Update `MeetingChat` (`chat.tsx`) with three distinct render styles: outgoing,
  incoming, and system; plus loading, empty, and error states with auto-scroll
- Update `MeetingSidebar` (`sidebar.tsx`) to accept the new message type and
  trigger history load when the chat tab becomes active
- Update `MeetingRoomContent` (`index.tsx`) to wire `useMeetingChat` and remove
  all mock data references
- Update `MeetingToolbar` (`toolbar.tsx`) to display an unread-message badge
  when the sidebar is closed
- Add i18n keys (`chatLoading`, `chatEmpty`, `chatError`, `chatRetry`,
  `chatSendError`, `systemMessageJoined`, `systemMessageLeft`) to `en.json` and
  `vi.json` under `meetingRoom`

## Capabilities

### New Capabilities

- `web-in-meeting-chat`: Real-time in-meeting chat for the web client — history
  loading from the REST API, message sending via the SDK, live delivery via
  LiveKit data channel, three message render types, unread badge, and full UI
  states (loading / empty / error)

### Modified Capabilities

None.

## Impact

- **Files deleted**: `frontends/web/src/lib/mock-data/messages.ts`
- **Files created**: `frontends/web/src/hooks/use-meeting-chat.ts`
- **Files modified**:
    - `frontends/web/src/components/meeting/index.tsx`
    - `frontends/web/src/components/meeting/chat.tsx`
    - `frontends/web/src/components/meeting/sidebar.tsx`
    - `frontends/web/src/components/meeting/toolbar.tsx`
    - `frontends/web/src/i18n/messages/en.json`
    - `frontends/web/src/i18n/messages/vi.json`
- **External dependencies**: `livekit-client` `RoomEvent.DataReceived` (already
  installed); generated SDK functions `sendMessage`, `getMessages`, `getRoom`
  (already generated, unused)
- **No API or schema changes** — backend is fully implemented
