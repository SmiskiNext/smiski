# Tasks

## 1. Domain contracts and use cases

- [x] 1.1 Finalize chat domain models/enums to represent outgoing, incoming, and
      system messages with sequence/order metadata
- [x] 1.2 Replace placeholder chat repository contract methods for history load,
      message send, and real-time message stream consumption
- [x] 1.3 Implement/complete use cases for loading chat history and sending text
      messages using `meetingId` as `roomId` ← (verify: use cases enforce
      active-meeting-only assumptions, map success/failure consistently with
      domain contracts)

## 2. Data layer integration with backend chat API

- [x] 2.1 Implement `ChatMessageMapper` for REST and LiveKit payload-to-domain
      conversion, including system type handling
- [x] 2.2 Implement `ChatRepositoryImpl` with `ChatApi.getRoom`,
      `getMessages(roomId, size, beforeSeqNum)`, and
      `sendMessage(roomId, request)` integration
- [x] 2.3 Add deterministic merge/de-duplication logic for history + real-time
      messages by id/seqNum ordering rules ← (verify: no duplicate rows and
      stable order under mixed history/realtime arrival)

## 3. LiveKit real-time chat reception path

- [x] 3.1 Extend existing LiveKit repository/listener to receive reliable
      data-message callbacks
- [x] 3.2 Parse UTF-8 JSON chat payload fields (`id`, `seqNum`, `senderId`,
      `senderName`, `content`, `type`, `createdAt`) and publish chat events
      upstream
- [x] 3.3 Handle malformed/unexpected payloads safely without breaking call
      state or chat surface ← (verify: invalid packets are ignored safely and
      call connection state remains healthy)

## 4. Presentation state and UI wiring

- [x] 4.1 Implement `MeetingChatViewModel` state model for loading, empty,
      content, error, and send-in-progress/failure events
- [x] 4.2 Implement/complete RecyclerView adapter with outgoing, incoming, and
      system message view types plus dedicated XML item layouts
- [x] 4.3 Ensure system messages are rendered as centered gray text according to
      product decision
- [x] 4.4 Wire `MeetingChatBottomSheet` to ViewModel observers, adapter updates,
      input send action, retry handling, and scroll behavior
- [x] 4.5 Pass and validate `meetingId`/`roomId` from active call flow into chat
      open/send/load path, enforcing active-meeting-only chat access ← (verify:
      opening chat from active call loads history, sending works, receiving
      updates live, and non-active state does not start chat operations)

## 5. Video call controls spec-aligned behavior update

- [x] 5.1 Remove unread chat badge/count assumptions from in-call overflow chat
      action behavior
- [x] 5.2 Keep participant count rendering for Participants action and preserve
      Chat/Participants navigation within `ActiveCallFragment` ← (verify:
      overflow sheet shows participant count only; chat action has no unread
      indicator)

## 6. Automated tests and verification readiness

- [x] 6.1 Add mapper unit tests for API/LiveKit payload conversion, including
      system message cases and malformed payload handling
- [x] 6.2 Add repository or use-case tests for load/send success and failure
      mappings
- [x] 6.3 Add `MeetingChatViewModel` tests for initial loading, empty/content
      transitions, send failure, and real-time receive merge behavior
- [x] 6.4 Run affected Android unit test suites and confirm deterministic pass
      for new chat scenarios ← (verify: new mapper/repository-use case/ViewModel
      tests pass and cover required state transitions)
