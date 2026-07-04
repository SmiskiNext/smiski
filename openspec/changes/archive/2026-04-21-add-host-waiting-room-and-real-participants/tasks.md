# Tasks

## 1. Host waiting room domain, data, and DI foundation

- [x] 1.1 Add `JoinRequestItem` domain model and `WaitingRoomRepository`
      interface with pending-list and moderation contracts
- [x] 1.2 Implement `MeetingEventSseClient` for host
      `GET /api/v1/meetings/{id}/events` stream with typed callbacks for
      `join_request_created`, `join_request_expired`, and `participant_kicked`
- [x] 1.3 Implement `WaitingRoomRepositoryImpl` using generated
      `JoinRequestsApi` and `MeetingsApi` for list, approve, deny, and
      approve-all operations
- [x] 1.4 Bind waiting-room repository implementation in `RepositoryModule` and
      verify Hilt graph compiles for waiting-room dependencies ← (verify:
      repository bindings resolve cleanly and all waiting-room API/SSE
      dependencies are injectable)

## 2. Host waiting room call lifecycle and SSE reconnection

- [x] 2.1 Extend `CallViewModel` with host waiting-room state exposure (pending
      list/count, stream status) and host/waitingRoomEnabled gating
- [x] 2.2 Add `CallViewModel` host SSE connect/disconnect orchestration tied to
      active call lifecycle entry and end/leave transitions
- [x] 2.3 Implement exponential-backoff reconnect scheduling (1s, 2s, 4s, capped
      at 30s) for unexpected host SSE disconnects
- [x] 2.4 Trigger pending join-request list resync after successful reconnect
      and publish synchronized state for badge/sheet consumers ← (verify:
      disconnect/reconnect path rehydrates pending list correctly and badge
      count matches server list)

## 3. Waiting room UI integration in active call flow

- [x] 3.1 Add host-only waiting-room toolbar action to `ActiveCallFragment` and
      wire visibility to host + waiting-room-enabled conditions
- [x] 3.2 Implement pending badge rendering on waiting-room action and keep it
      synchronized with `CallViewModel` pending count
- [x] 3.3 Create `WaitingRoomViewModel` and `JoinRequestAdapter` to drive
      loading/error/empty/has-items states and per-item moderation actions
- [x] 3.4 Build `WaitingRoomBottomSheet` with header, retry/error handling,
      approve/deny item actions, and approve-all action
- [x] 3.5 Add `layout_waiting_room_sheet.xml` and `item_join_request.xml`, then
      connect snackbar-based API error feedback paths ← (verify: host can open
      sheet, process approve/deny/approve-all, and see correct state transitions
      for loading/error/empty/items)

## 4. Real participant list domain and repository refactor

- [x] 4.1 Refactor `Participant` model to include `id` and `role` enum (`HOST`,
      `PARTICIPANT`, `GUEST`) while removing mock-only fields
- [x] 4.2 Add `ParticipantRepository` interface and `ParticipantRepositoryImpl`
      using generated `ParticipantsApi` for meeting participant metadata
- [x] 4.3 Bind participant repository in `RepositoryModule` and update any
      impacted constructors/usages to new participant model contract ← (verify:
      project compiles with new participant model and repository dependency
      wiring)

## 5. ParticipantsViewModel and bottom-sheet real data merge

- [x] 5.1 Rewrite `ParticipantsViewModel` with injected dependencies and remove
      mock-data initialization path
- [x] 5.2 Consume LiveKit participants from activity-scoped `CallViewModel` and
      map realtime mic/camera state into participant UI model
- [x] 5.3 Call participants API once per sheet session to fetch roles and merge
      with LiveKit list using identity-first matching and display-name fallback
- [x] 5.4 Implement API-failure fallback that still publishes LiveKit-only list
      without blocking bottom-sheet interaction
- [x] 5.5 Update `ParticipantsBottomSheet` integration to pass call participant
      context into `ParticipantsViewModel` and observe merged output ← (verify:
      merged list updates in real time and remains usable when role enrichment
      fails)

## 6. Participant adapter and end-to-end regression validation

- [x] 6.1 Update `ParticipantAdapter` binding logic for refactored model fields
      and Host/Guest badge rendering rules
- [x] 6.2 Validate active-call participants and waiting-room flows together for
      host and non-host sessions, including hidden host-only controls for
      non-hosts
- [x] 6.3 Run Android build/tests and perform manual scenario checks for SSE
      reconnect sync, moderation actions, and participants fallback behavior ←
      (verify: all spec scenarios are covered and no regressions in active call
      navigation/state management)
