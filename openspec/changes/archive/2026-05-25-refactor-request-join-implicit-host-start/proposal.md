## Why

Hosts cannot start a scheduled meeting from the upcoming-meeting card on web or
Android. Both clients navigate directly into the green-room / pre-join flow,
which calls `requestJoin`. Because the meeting is still `SCHEDULED`,
`RequestJoinUseCase` rejects the call with `INVALID_STATUS_TRANSITION`. The
fix-by-symptom (have every client call `:start` first) is fragile: the web
instant flow already does it with a brittle two-round-trip plus empty-token
handoff, and three independent call sites must each remember the orchestration.
Centralising the lifecycle decision in the backend resolves the bug, removes
duplicate orchestration, and lets the `:start` endpoint and its callers be
deleted.

## What Changes

- Backend `RequestJoinUseCase` SHALL implicitly transition a `SCHEDULED` meeting
  to `LIVE` when the requester is the host, before evaluating the join.
  `MeetingStartedEvent` SHALL still be published exactly once via the existing
  `findByIdWithLock` row lock so concurrent host devices do not double-publish.
- **BREAKING**: Delete the `POST /v1/meetings/{id}:start` endpoint and its
  application use case (`StartMeetingUseCase`, `StartMeetingCommand`). No
  deprecation window; the only callers live in this repository.
- Web instant-meeting flow simplifies to a single `createInstantMeeting` call
  followed by navigation to the green-room route. The `STARTING` state, the
  `startMeeting` call, and the `MEETING_TOKEN_KEY` / `MEETING_ROOM_KEY` /
  `MEETING_ID_KEY` session-storage handoff for instant launches SHALL be
  removed. The success dialog with the shareable short code stays.
- Non-host requesters SHALL continue to receive `INVALID_STATUS_TRANSITION` when
  the meeting is not `LIVE`. Host requesters SHALL continue to receive
  `INVALID_STATUS_TRANSITION` when the meeting is `ENDED` or `CANCELLED`.
- Web SDK regenerates from the unified OpenAPI spec; Android source is not
  edited but its build is exercised to confirm the regenerated client still
  compiles and tests pass.

## Capabilities

### New Capabilities

- `meeting-host-implicit-start`: Backend rule that the host's join request on a
  scheduled meeting implicitly drives the lifecycle transition to live, with
  idempotent and concurrency-safe semantics. Captures the contract the
  `meeting-management` service exposes to all clients.

### Modified Capabilities

- `web-meeting-creation`: Instant-meeting workflow no longer calls
  `startMeeting`, no longer maintains a `STARTING` phase, and no longer hands
  off room credentials from the create flow. After success, the host is sent to
  the green-room route, which performs the join.
- `web-join-meeting`: The instant-launch handoff requirement (storing token,
  room name, and meeting identifier from the create flow) is removed. The
  approved-join handoff path is unchanged. Join semantics now allow a host
  requesting a `SCHEDULED` meeting to receive an approved response.

## Impact

- Affected services: `services/meeting-management` (use case, controller,
  command, tests, generated OpenAPI).
- Affected clients: `frontends/web/src/components/create-meeting/*` and the
  generated SDK at `frontends/web/src/generated/*`.
- Unchanged sources: Android app source (`frontends/android-app`), but the build
  is still exercised because it consumes the unified OpenAPI spec.
- Domain model unchanged: `Meeting.start()`, `MeetingStatus.canTransitionTo`,
  Flyway schema, authorisation rules.
- Event consumers (`chat-management`, `notification`) unchanged: they still
  observe `MeetingStartedEvent` published through the outbox.
