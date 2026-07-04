# Context

Android call surfaces already include entry points and placeholder components
for in-meeting chat, but the chat domain/data/presentation pipeline is
incomplete and does not deliver production behavior. The backend contract is
already available through chat-management REST endpoints and LiveKit reliable
data packets, and meeting lifecycle rules already govern chat-room availability
(active meetings only, archived after end/cancel).

The Android app uses XML + Fragments, MVVM + Clean Architecture, Hilt, Retrofit,
and LiveData. The design must stay inside that architecture, avoid Compose
migration, and implement only basic text chat (no reply threading, no
attachments, no unread badge).

## Goals / Non-Goals

**Goals:**

- Deliver a full in-meeting chat pipeline for Android during active calls only.
- Implement history loading via existing ChatApi and map transport models into
  stable domain models.
- Implement message send flow through existing backend endpoint and update local
  chat timeline deterministically.
- Add real-time message receiving through the existing LiveKit room integration
  by handling reliable data packets carrying chat payloads.
- Implement `MeetingChatViewModel` state orchestration for loading, empty,
  success, error, sending state, and incoming updates.
- Render outgoing, incoming, and system messages distinctly in
  `MeetingChatBottomSheet`, with system messages centered gray text.
- Keep implementation cohesive with existing architecture and add practical unit
  tests for mapper/repository or use cases and ViewModel.

**Non-Goals:**

- Reply threading, file attachments, reactions, editing/deleting messages.
- Unread badge/count behaviors for chat action UI.
- New backend APIs, protocol changes, or non-LiveKit real-time channels.
- UI framework migration (Compose) or broad call-screen redesign beyond required
  chat wiring.

## Decisions

### 1) Use meetingId as chat roomId end-to-end in Android chat flow

- **Decision:** The chat feature will pass the existing meeting identifier from
  call flow into chat as `roomId` without introducing extra translation layers.
- **Rationale:** Backend explicitly uses meetingId as roomId, so direct reuse
  avoids mapping bugs and keeps interfaces simple.
- **Alternatives considered:**
    - Resolve roomId separately through additional lookup each open: rejected as
      redundant and latency-adding.
    - Store a dedicated chat-room identifier in new local state: rejected as
      unnecessary complexity.

### 2) Keep ChatRepository as single source for REST history/send and LiveKit incoming merge

- **Decision:** ChatRepository contract will expose operations for initial
  message retrieval, pagination-ready retrieval parameters, sending text, and
  consuming real-time incoming messages as stream-like updates that ViewModel
  can observe.
- **Rationale:** Centralizing chat transport concerns in repository preserves
  Clean Architecture boundaries and avoids UI-layer protocol handling.
- **Alternatives considered:**
    - Let ViewModel parse LiveKit packets directly: rejected due to leaking
      infrastructure concerns into presentation.
    - Separate repository for REST and a second manager for LiveKit packets:
      rejected to minimize moving parts for this scoped feature.

### 3) Integrate LiveKit chat packet handling into existing room event path

- **Decision:** Extend the current LiveKit repository/listener pipeline with
  data-message callback handling and publish parsed chat events upward in
  existing observable channels.
- **Rationale:** Real-time source of truth for new messages is LiveKit data
  packets; integrating at existing room-event boundary avoids parallel
  socket/event subsystems.
- **Alternatives considered:**
    - Poll message history endpoint for updates: rejected due to poorer UX and
      unnecessary backend load.
    - Add SSE/WebSocket for chat: rejected because backend does not provide such
      endpoint and scope forbids protocol expansion.

### 4) Represent chat UI state with explicit state model and deterministic message ordering

- **Decision:** `MeetingChatViewModel` will maintain explicit state buckets
  (`Loading`, `Empty`, `Content`, `Error`) plus send-in-progress and one-shot
  error events, while keeping message list ordered by sequence number then
  createdAt fallback.
- **Rationale:** Explicit states are easier to test and map cleanly to XML
  fragment rendering; deterministic ordering prevents duplicate/inconsistent
  timeline under mixed history + real-time updates.
- **Alternatives considered:**
    - Single mutable list + booleans: rejected as fragile and harder to
      validate.
    - Paging-only solution initially: rejected because current scope requires
      minimal cohesive implementation, not full paging architecture overhaul.

### 5) Message item rendering uses multi-view-type RecyclerView adapter

- **Decision:** Implement dedicated item view types/layouts for outgoing,
  incoming, and system messages.
- **Rationale:** Existing UI uses RecyclerView; multi-view-type adapter is the
  least-disruptive way to satisfy message-type visuals, especially centered gray
  system messages.
- **Alternatives considered:**
    - Single layout with runtime style toggles: rejected for
      maintainability/readability.
    - Epoxy/Compose migration: rejected as out of scope.

### 6) Remove unread-badge requirement from in-call overflow chat action behavior

- **Decision:** Update affected call-controls capability so chat action remains
  available but no unread badge is required.
- **Rationale:** User-confirmed scope excludes unread badge and keeps chat
  basic.
- **Alternatives considered:**
    - Keep badge as optional best-effort: rejected because it conflicts with
      confirmed product decision.

### 7) Testing scope prioritizes transformation and state logic

- **Decision:** Add unit tests for mapper conversions, repository/use-case
  success/failure mapping, and ViewModel state transitions for load/send/receive
  paths.
- **Rationale:** These layers carry highest logic risk and are practical to test
  without heavy UI instrumentation.
- **Alternatives considered:**
    - Full UI instrumentation for bottom sheet: deferred due to cost/benefit for
      this change size.

## Risks / Trade-offs

- **[Risk] LiveKit data payload variability or malformed JSON can break receive
  flow** → **Mitigation:** defensive parsing, ignore invalid packets safely, and
  surface non-fatal diagnostics without crashing chat UI.
- **[Risk] Duplicate messages when sent message later arrives via LiveKit
  broadcast** → **Mitigation:** de-duplicate by message id and/or sequence
  number before list insertion.
- **[Risk] Out-of-order arrival between history fetch and real-time packets** →
  **Mitigation:** stable sorting rule and merge strategy keyed by seqNum,
  preserving deterministic render order.
- **[Risk] Chat opened when meeting is no longer active** → **Mitigation:** gate
  entry and repository operations by active-call state and show recoverable
  empty/error state when room is unavailable.
- **[Trade-off] LiveData-centric state model instead of coroutine Flow-first
  redesign** → **Mitigation:** keep implementation aligned with existing
  architecture to reduce integration risk; future migration can be planned
  separately.

## Migration Plan

1. Implement domain/data contracts and mapper/repository behavior behind
   existing chat abstractions.
2. Extend LiveKit repository event handling to emit chat data-message events.
3. Implement ViewModel state machine and connect `MeetingChatBottomSheet` UI +
   adapter.
4. Pass/validate roomId from active call flow where required.
5. Run and stabilize unit tests for mapper/repository-use case/ViewModel.
6. Rollout with no backend migration requirements since existing APIs/protocol
   are reused.

Rollback strategy: disable chat entry trigger from active call and/or revert
Android chat module changes; no backend schema or contract rollback needed.

## Open Questions

- Whether the backend `type` field includes additional system subtypes beyond
  current expected set and how unknown types should be labeled in UI.
- Exact existing localization keys to reuse for chat error/empty/loading strings
  versus adding new keys.
- Whether message history pagination should be wired now beyond initial page
  size or deferred to a follow-up capability.
