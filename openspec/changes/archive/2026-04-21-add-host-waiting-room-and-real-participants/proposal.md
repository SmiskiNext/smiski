# Why

Hosts currently cannot manage waiting-room requests from inside the Android
active call, and the participants sheet still renders hardcoded mock users
instead of live call data. Delivering host-side waiting-room controls and a real
participant roster is needed now to make in-call moderation and participant
visibility production-ready.

## What Changes

- Add host waiting-room management in the Android active call flow using host
  meeting SSE events, pending request synchronization, and join-request
  moderation APIs.
- Add a host-only waiting-room toolbar action in `ActiveCallFragment` with a
  pending-request badge and a new `WaitingRoomBottomSheet` for list, approve,
  deny, and approve-all actions.
- Add host waiting-room lifecycle orchestration to `CallViewModel` including
  connect/disconnect timing, exponential-backoff reconnect, and list resync
  after reconnect.
- Replace mock participant data in `ParticipantsViewModel` with merged real-time
  LiveKit participant state plus backend role enrichment from participants API.
- Refactor participant presentation model and adapter bindings to support role
  badges (Host/Guest) and remove mock-only fields.
- Introduce participant and waiting-room domain/data repositories and wire them
  through Hilt modules.

## Capabilities

### New Capabilities

- `android-host-waiting-room`: Host in-call waiting-room monitoring and
  moderation via SSE + join-request APIs, including toolbar badge and moderation
  bottom sheet states.
- `android-real-participant-list`: Real participants sheet data source and merge
  behavior using LiveKit participant state plus backend participant role
  enrichment.

### Modified Capabilities

- `android-videocall-shell`: Update active-call and participants-sheet
  requirements to replace placeholder participant behavior and include host
  waiting-room entry integration in active call surfaces.

## Impact

- Android app call stack under
  `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/videocall/`
  and `presentation/meeting/participant/`.
- Domain/data/DI layers for new repositories and SSE client integration
  (`domain/model`, `domain/repository`, `data/repository`, `data/remote/sse`,
  `di/RepositoryModule`).
- Existing generated OpenAPI interfaces usage (`JoinRequestsApi`, `MeetingsApi`,
  `ParticipantsApi`) and call-time orchestration in `CallViewModel`.
- Active call XML/UI resources for waiting-room toolbar badge and bottom-sheet
  layouts.
