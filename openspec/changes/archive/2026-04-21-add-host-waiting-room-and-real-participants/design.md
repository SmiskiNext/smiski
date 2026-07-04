# Context

The Android video-call experience already supports pre-join approval SSE for
guests and exposes activity-scoped call state through `CallViewModel`, but
host-side waiting-room moderation is missing from the active call flow. Hosts
currently have no in-call mechanism to observe or process pending join requests,
so moderation requires out-of-band handling and introduces admission delays.

The participants sheet also remains in a placeholder state where
`ParticipantsViewModel` emits static mock entries. Real-time participant state
already exists via LiveKit in `CallViewModel`, and backend participant metadata
is available through `GET /api/v1/meetings/{id}/participants`, but these sources
are not merged for UI consumption.

This change spans domain models, repository contracts, data integration (SSE +
OpenAPI clients), DI wiring, activity-scoped call lifecycle, and bottom-sheet
UI. It must follow existing Android conventions in `app/codemap.md`: Java, MVVM,
Hilt, LiveData, XML layouts, and CompletableFuture-based async orchestration.

## Goals / Non-Goals

**Goals:**

- Provide host-only waiting-room management during active calls with pending
  badge visibility, list synchronization, and approve/deny controls.
- Introduce robust host SSE handling for meeting events with lifecycle-aware
  connect/disconnect behavior and reconnect backoff.
- Ensure reconnect safety by re-fetching pending join requests after stream
  restoration.
- Replace participants mock data with merged real sources: LiveKit participant
  state as primary and backend participant role enrichment as secondary.
- Keep participants UI resilient when backend role enrichment fails by
  continuing with LiveKit-only rendering.
- Align new data/repository/viewmodel code with existing dependency-injection
  and presentation patterns.

**Non-Goals:**

- Implement participant-kicked UX flows beyond informational event handling.
- Add server-side API changes, SSE protocol changes, or OpenAPI contract
  changes.
- Introduce pagination UX for waiting-room list beyond current API paging needs
  for pending fetch aggregation.
- Rework call screen architecture beyond required waiting-room and participants
  integrations.

## Decisions

### 1) Host waiting-room SSE is owned by CallViewModel lifecycle

`CallViewModel` will orchestrate host SSE connection and disconnection because
it is activity-scoped across call fragments and already owns meeting/call
lifecycle state.

- Rationale: Keeps stream lifecycle aligned with active call lifetime and avoids
  fragment recreation leaks.
- Alternative considered: Managing SSE in `ActiveCallFragment` directly.
  Rejected because fragment lifecycle churn can cause duplicate streams and
  reconnect instability.

### 2) Introduce dedicated `MeetingEventSseClient` patterned after existing guest SSE client

A new data-layer SSE client will model host stream consumption
(`/api/v1/meetings/{id}/events`) with typed callbacks for
`join_request_created`, `join_request_expired`, and `participant_kicked`.

- Rationale: Reuses proven OkHttp EventSource pattern while separating guest
  request stream responsibilities from host meeting-event stream
  responsibilities.
- Alternative considered: Extending `JoinRequestSseClient` for both modes.
  Rejected to avoid overloading one class with divergent URL/event semantics.

### 3) Reconnection strategy uses exponential backoff with sync-on-reconnect

Reconnect delay progression is 1s, 2s, 4s ... capped at 30s. After reconnect
success, the app re-fetches pending join requests from join-request list API and
replaces local pending state.

- Rationale: Bounded retry avoids aggressive reconnect storms while list refresh
  repairs missed events and ordering gaps.
- Alternative considered: Event replay assumption without post-reconnect sync.
  Rejected because SSE is transient and dropped events would leave inconsistent
  badge/list state.

### 4) Waiting-room state is centralized in a new WaitingRoomViewModel backed by WaitingRoomRepository

`WaitingRoomViewModel` will expose bottom-sheet UI states (Loading, Error,
Empty, HasItems), pending list, badge count, and moderation action states;
repository encapsulates API calls and pagination handling.

- Rationale: Separates call-level stream orchestration from sheet-specific
  list/action UI logic while preserving MVVM boundaries.
- Alternative considered: Putting all waiting-room state into `CallViewModel`.
  Rejected to prevent call viewmodel bloat and tighter coupling to bottom-sheet
  rendering details.

### 5) Participant list merge uses LiveKit-first composition with one-time role enrichment

`ParticipantsViewModel` receives LiveKit participant updates from
`CallViewModel` and performs one backend enrich fetch when participants sheet
opens. Merging keys are identity/displayName; unmatched LiveKit entries default
to `PARTICIPANT` role.

- Rationale: LiveKit is the authoritative real-time presence/media source, while
  backend role metadata is supplemental and slower-changing.
- Alternative considered: Backend participant list as primary source. Rejected
  because it cannot represent near-real-time mic/camera/speaker state reliably.

### 6) Participant model is refactored to production fields only

`Participant` domain model will keep stable identifiers and media/name fields,
add enum role (`HOST`, `PARTICIPANT`, `GUEST`), and remove mock-only fields.

- Rationale: Eliminates UI mock artifacts and creates a clean contract for
  adapter binding and future participant actions.
- Alternative considered: Keeping legacy fields for compatibility. Rejected
  because it preserves dead state and increases adapter ambiguity.

## Risks / Trade-offs

- [SSE reconnect race with fragment transitions] → Mitigation: gate
  connect/disconnect through explicit call-state transitions in `CallViewModel`
  and ensure idempotent client cancel/connect methods.
- [Badge/list drift when events arrive during API moderation actions] →
  Mitigation: update local list optimistically only on successful API responses
  and perform periodic or reconnect-triggered full list refresh.
- [Participant role matching collisions using display names] → Mitigation:
  prefer stable identity/id matching first, fallback to displayName only when
  required.
- [Role enrichment API failure degrades badge visibility] → Mitigation: render
  LiveKit-only list without blocking sheet and omit role badge when role is
  unknown/default.
- [Increased ViewModel complexity across call and bottom sheets] → Mitigation:
  keep waiting-room and participants responsibilities in dedicated viewmodels
  and repository abstractions.

## Migration Plan

1. Add new domain/data/DI components (`JoinRequestItem`,
   `WaitingRoomRepository`, `ParticipantRepository`, related implementations,
   and SSE client).
2. Integrate host waiting-room entry and badge in `ActiveCallFragment` with new
   `WaitingRoomBottomSheet` UI resources.
3. Add call-lifecycle SSE orchestration in `CallViewModel` for host waiting-room
   enabled calls.
4. Refactor participants model/viewmodel/adapter and wire data merge from
   `CallViewModel` + `ParticipantsApi`.
5. Validate behavior with targeted manual scenarios: host join-request flow,
   reconnect sync, approve/deny/all actions, and participant-sheet fallback on
   enrich failure.
6. Rollback strategy: disable new waiting-room UI entry and revert to previous
   participant rendering by restoring prior viewmodel and adapter bindings if
   critical regressions appear.

## Open Questions

- Whether backend participant payload always includes a stable identifier that
  matches LiveKit identity directly; if not, matching precedence may require
  refinement during implementation.
- Whether waiting-room pending list should cap visible items or load all
  available pages in the first release; this design assumes complete pending
  aggregation for correctness.
- Whether `participant_kicked` should eventually surface host-facing
  toast/snackbar feedback; currently scoped as informational event handling
  only.
