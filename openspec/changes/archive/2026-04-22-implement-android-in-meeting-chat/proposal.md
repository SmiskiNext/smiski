# Why

Android already exposes an in-call chat entry surface, but the actual message
pipeline is not implemented end-to-end. Completing in-meeting chat now is
necessary to support real meeting communication using the existing backend APIs
and LiveKit real-time transport without introducing a parallel architecture.

## What Changes

- Implement full Android in-meeting chat flow for active meetings only,
  including history load, text message send, and real-time receive.
- Replace placeholder chat domain/repository/use-case components with production
  implementations aligned to existing MVVM + Clean Architecture patterns.
- Integrate LiveKit data-packet message reception into the existing call room
  path for chat updates.
- Complete chat UI rendering in `MeetingChatBottomSheet` using RecyclerView item
  types for outgoing, incoming, and system messages.
- Standardize state handling for loading, empty, success, and recoverable error
  states in chat ViewModel/UI.
- Add targeted automated tests for mapper, repository/use case logic, and
  ViewModel behaviors.
- **BREAKING** Remove unread badge/count expectations for in-call chat action
  surfaces from Android call controls requirements.

## Capabilities

### New Capabilities

- `android-in-meeting-chat`: Full active-meeting chat behavior in Android,
  covering history retrieval, real-time message ingestion via LiveKit data
  channel, message sending, and chat UI state/rendering.

### Modified Capabilities

- `android-video-call-layout-controls`: Update in-call overflow/chat action
  expectations to remove unread badge/count requirements and keep chat access
  behavior aligned with basic chat scope.

## Impact

- Affected Android app layers: presentation (`ActiveCallFragment`,
  `MeetingChatBottomSheet`, `MeetingChatViewModel`, chat adapter/layouts),
  domain (chat models/use cases), and data (chat repository + mapper + Retrofit
  API integration + LiveKit event bridge).
- Uses existing backend contracts only: chat-management REST (`getRoom`,
  `getMessages`, `sendMessage`) and LiveKit reliable data packets for real-time
  updates.
- No new backend API endpoints, no Compose migration, and no expansion into
  reply/attachment/unread-badge feature scope.
