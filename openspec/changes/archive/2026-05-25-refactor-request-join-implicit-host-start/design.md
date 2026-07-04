## Context

The meeting lifecycle is `SCHEDULED → LIVE → ENDED`, with
`SCHEDULED → CANCELLED` as the alternate exit. Today, transitioning
`SCHEDULED → LIVE` is an explicit step that clients must perform by calling
`POST /v1/meetings/{id}:start`. Three call sites exist:

1. Web instant-meeting flow (`use-create-meeting.ts`) — calls `:start`
   immediately after `createInstantMeeting`, then hands off to the meeting-room
   page using session-storage. The handoff token is empty because `:start`
   returns no LiveKit credentials.
2. Web upcoming-meeting cards (`upcoming-meeting-card.tsx`,
   `meeting-detail-dialog.tsx`) — push to the green-room route, where the user
   submits a join request. The card never calls `:start`.
3. Android dashboard (`DashboardFragment.onJoinMeetingClicked`) — launches
   `VideoCallActivity`, which leads into the pre-join fragment and then a join
   request. The dashboard never calls `:start`.

Sites 2 and 3 produce the bug: the join request is rejected with
`INVALID_STATUS_TRANSITION` because the meeting is still `SCHEDULED`. Site 1
works but couples the create flow to room-launch state it does not own.

The lifecycle decision is a backend concern. Pushing it to the backend removes
three orchestration responsibilities and lets the `:start` endpoint go away
entirely.

## Goals / Non-Goals

**Goals:**

- A host's `requestJoin` against a `SCHEDULED` meeting SHALL return a normal
  approved/pending response and the meeting SHALL be `LIVE` afterwards.
- The transition SHALL be idempotent under concurrent host requests, with
  exactly one `MeetingStartedEvent` emitted per actual SCHEDULED → LIVE
  transition.
- Non-host callers SHALL keep the same `INVALID_STATUS_TRANSITION` error when
  the meeting is not yet `LIVE`.
- The web instant-meeting flow SHALL not call `:start` and SHALL not maintain a
  `STARTING` phase or instant-launch session-storage handoff.
- The `:start` endpoint, its use case, and its command SHALL be removed.

**Non-Goals:**

- Changes to `Meeting.start()`, `MeetingStatus.canTransitionTo()`, or the Flyway
  schema.
- Changes to authorization (host check), guest admission flow, password
  enforcement, or LiveKit token issuance logic.
- Changes to `:end`, `:cancel`, instant vs scheduled meeting types.
- Changes to Android source code. Android consumes the regenerated SDK only.
- Migration support for non-repository clients of `:start` — none are known.

## Decisions

### Decision 1: Place implicit elevation inside `RequestJoinUseCase`, before LIVE check

The host elevation runs inside the same `@Transactional` use case that already
performs `findByIdWithLock`. Sequence:

```
findByIdWithLock(meetingId)
if missing → MeetingNotFound
if requester is host:
   if status == SCHEDULED → meeting.start() (registers MeetingStartedEvent)
   if status in {ENDED, CANCELLED} → InvalidStatusTransition(status, LIVE)
   if status == LIVE → no-op (idempotent)
else (non-host):
   if status != LIVE → InvalidStatusTransition(status, LIVE)
... continue existing join logic (admission, password, LiveKit, log)
save meeting
publish PublishableEvents (includes MeetingStartedEvent if elevated)
```

**Why here, not a domain method on `Meeting`:** `Meeting.start()` already
encapsulates the SCHEDULED → LIVE transition with event registration. The use
case is the right place to decide _whether_ to call it based on the caller's
role and current status. Moving the rule into the domain would couple the
aggregate to a "who is requesting" concept that does not belong there.

**Why before the LIVE check, not by relaxing the LIVE check:** Relaxing the
check would lose the explicit lifecycle event; we still need
`MeetingStartedEvent` for downstream consumers. The pre-check elevation pattern
keeps the lifecycle state machine and event publication intact.

**Alternative considered:** Keep `:start` and have clients call it from the
upcoming-card path. Rejected: triples the orchestration responsibility, fails on
race when two host devices click "Start" near-simultaneously, and doesn't
simplify the instant flow.

### Decision 2: Idempotent under host concurrency via existing row lock

`findByIdWithLock` already takes a row-level lock for the duration of the
transaction. A second host request waits until the first commits, then sees
`status == LIVE` and skips elevation. This means `MeetingStartedEvent` is
registered only by the request that actually performed the transition.

**Why not optimistic concurrency:** The use case already pessimistically locks.
Adding optimistic checks would be redundant and would risk surfacing retry
errors to a host who clicked twice — bad UX for a flow that is naturally
idempotent.

### Decision 3: Delete the `:start` endpoint outright (no deprecation)

All in-repo callers go away as part of this change. No external integrators are
documented. The endpoint is removed in the same change so the OpenAPI contract
reflects the simpler model immediately.

**Why no `Deprecated` window:** Keeping it would force two migration steps
(deprecate now, remove later) for no real consumer. Removing it now means one
regenerated SDK and the Android build's compile is the canary.

**Alternative considered:** Keep `:start` but make it a no-op when meeting is
already LIVE. Rejected: leaves a dead endpoint that future readers will have to
investigate, and tempts new callers to use the wrong flow.

### Decision 4: Web instant flow simplifies to create + navigate to green-room

`useCreateMeeting` reduces to two phases plus error: `IDLE → CREATING → READY`.
The `READY` payload is `{ meetingId, shortCode }` only — token and room name are
no longer present because the create flow does not own them. After the host
clicks "Continue" on the success dialog, navigation goes to
`/{locale}/workspace/green-room?code={shortCode}`, which already knows how to
perform `requestJoin` and store handoff credentials for the meeting room.

**Why not skip the success dialog?** The dialog gives the host a moment to copy
the link before entering the room — useful when scheduling needs sharing first.
Keeping it preserves existing UX and a localized feature.

**Why route through green-room instead of meeting-room directly?** The
green-room route already handles meeting lookup, password prompt, and the
`requestJoin` lifecycle including the new implicit elevation. Reusing it avoids
a second copy of the join code and keeps the instant flow on the same join
contract as the upcoming-meeting flow.

### Decision 5: SDK regeneration is the only Android-side action

Android source already lacks a `startMeeting` call. Removing the endpoint from
the unified OpenAPI causes the regenerated client to drop the method. The
Android build is exercised to confirm nothing references the removed SDK symbol.

## Risks / Trade-offs

- [Risk] **Implicit lifecycle change is less discoverable than a dedicated
  endpoint.** Engineers reading `requestJoin` will need to notice the
  host-elevation branch. → **Mitigation**: Javadoc on
  `RequestJoinUseCase.execute` documenting the elevation rule; dedicated unit
  tests covering each transition matrix cell; scenario name "host requestJoin
  SCHEDULED → APPROVED + LIVE" in test output.

- [Risk] **Existing tests assume host on SCHEDULED is rejected.** Any test that
  calls `requestJoin` as host on SCHEDULED expecting a 422 will need to be
  flipped. → **Mitigation**: Sweep `RequestJoinUseCaseTest` and any controller
  test that hits the join endpoint with a host caller; convert the assertion or
  rename to its non-host equivalent.

- [Risk] **Removed endpoint causes 404 for stale clients in browser cache or old
  Android installs.** Real impact is small (only this repo ships callers). →
  **Mitigation**: Web build regenerates and ships fresh SDK with no
  `startMeeting` symbol. Old Android binaries do not call `:start` today.

- [Risk] **MeetingStartedEvent is published from the join transaction.**
  Downstream consumers (chat-management, notification) currently observe this
  event. Publishing from a different code path could cause double- publish if
  the old `StartMeetingUseCase` flow is somehow still reachable. →
  **Mitigation**: Deletion of `StartMeetingUseCase`, controller binding, and
  tests is part of this change. Outbox semantics are unchanged.

- [Trade-off] **`requestJoin` now mutates lifecycle in a "hot" path.**
  Acceptable because the existing implementation already mutates persistent
  state (join request, participation log) inside the same transaction.

## Migration Plan

1. Land all changes in one PR. There is no staged migration because no external
   clients consume `:start`.
2. After merge: confirm OpenAPI no longer lists `meetings/{id}:start` and the
   regenerated web SDK no longer exposes `startMeeting`.
3. Rollback: revert the merge commit. Domain semantics are unchanged so
   reverting the controller and use case files restores the previous behavior
   without data migrations.

## Open Questions

None at start. All decisions above are locked from the explore phase.
