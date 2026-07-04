# Context

The web meeting room already renders live participants, host-only recording and
waiting-room controls, and reactive participant media state from LiveKit.
However, host participants cannot currently moderate other participants from the
People tab even though the backend already exposes `muteAllParticipants` and
`muteParticipantTrack` endpoints and Android already ships equivalent controls
in its participants sheet.

This change touches three linked concerns: the generated web SDK must expose the
moderation endpoints, the meeting room state must identify which participant row
represents the host, and the sidebar People tab must gain host-only moderation
controls without regressing the existing read-only experience for non-host
users. The current meeting bootstrap already provides `hostId`, which is
sufficient to mark the host participant row without adding a new backend fetch
during room join.

## Goals / Non-Goals

**Goals:**

- Regenerate the web SDK so meeting moderation endpoints are callable from the
  web app.
- Extend meeting participant view models and sidebar props so host-only controls
  can be rendered deterministically.
- Add a sticky host-only mute-all action and inline per-participant microphone
  and camera mute actions in the People tab.
- Keep participant media state authoritative to LiveKit events instead of
  applying optimistic UI toggles.
- Provide localized loading, success, tooltip, and failure messaging consistent
  with the existing web meeting room UX.
- Add focused tests around rendering rules and any extracted moderation state
  helpers.

**Non-Goals:**

- Adding new backend APIs or changing existing moderation authorization rules.
- Adding participant removal, role reassignment, or other moderation actions
  beyond mute mic, mute camera, and mute all microphones.
- Introducing a new participant-role synchronization service or persistent role
  cache.
- Changing non-host sidebar behavior beyond preserving the current read-only
  participant status display.

## Decisions

### Use SDK regeneration as the source of truth for moderation operations

The implementation will regenerate `frontends/web/src/generated/` from
`openapi/unified-openapi.yaml` using the existing
`frontends/web/openapi-ts.config.ts` configuration, then import
`muteAllParticipants` and `muteParticipantTrack` from the generated client.

Rationale:

- Keeps the web app aligned with the authoritative OpenAPI contract.
- Avoids hand-written API wrappers that would drift from codegen conventions.
- Matches the requirement that the endpoints already exist in the spec but are
  missing from generated output.

Alternatives considered:

- Hand-writing temporary fetch helpers in the meeting room module. Rejected
  because it duplicates generated-client behavior and adds cleanup work later.

### Resolve the host row from existing `hostId` meeting data

Participant moderation visibility will be derived by comparing each participant
identity with the already-known `hostId` from meeting bootstrap data.
`ParticipantViewModel` will gain an optional `role` field so the sidebar can
explicitly mark the host as `HOST` and treat all others as moderable rows.

Rationale:

- Satisfies the UI requirement to hide moderation controls on host participant
  rows.
- Avoids an additional `getParticipants` fetch or dependence on LiveKit metadata
  that may not be consistently populated.
- Mirrors the Android rule that only non-host participants are mutable.

Alternatives considered:

- Fetching participant roles from `getParticipants` during room join. Rejected
  for this scope because it adds extra request orchestration and role-refresh
  concerns without changing the moderation decision.
- Reading roles from LiveKit participant metadata. Rejected because it depends
  on metadata encoding that is not established as a reliable contract here.

### Keep moderation state local to the sidebar and rely on LiveKit for final media state

Mute actions will call backend APIs and show per-action loading states in the
sidebar, but they will not optimistically flip participant mic or camera status.
Instead, the participant rows will update when LiveKit emits authoritative
track-muted state changes.

Rationale:

- Prevents the UI from showing a muted state before the room actually reflects
  it.
- Matches the design decision already made for reactive updates.
- Limits state management to request lifecycle feedback rather than duplicating
  room media state.

Alternatives considered:

- Optimistically toggling participant icons immediately after the button press.
  Rejected because backend and LiveKit timing could create temporary false state
  and force reconciliation logic.

### Scope success and error feedback by action type

The mute-all banner will own a small local finite state: idle, loading, and
transient success for two seconds before returning to idle. Per-participant
actions will show an inline spinner for the active track button only. Errors
will use the existing toast-style recoverable feedback pattern, while individual
success toasts are intentionally omitted to avoid notification spam.

Rationale:

- Gives hosts confirmation for the bulk action, which has broader impact and no
  per-row visual cue.
- Preserves a quiet UX for repeated participant-level actions.
- Follows existing web meeting settings patterns for async states.

Alternatives considered:

- Toasting every successful mute action. Rejected because it would flood the
  host during active moderation.
- Adding confirmation dialogs before each mute. Rejected by product decision and
  would slow down moderation.

### Preserve non-host rendering and extend host-only props explicitly

`MeetingSidebar` will receive explicit `isHost`, `meetingId`, `onMuteMic`,
`onMuteCamera`, and `onMuteAll` props, while non-host rendering remains
read-only and unchanged aside from internal prop plumbing. Host-only inline
camera controls will only appear on moderable rows, while non-host users
continue to see participant status without interactive controls.

Rationale:

- Keeps authorization and action availability clear at the component boundary.
- Minimizes ambiguous sidebar behavior across host and non-host contexts.
- Supports isolated rendering tests with mocked props.

Alternatives considered:

- Having `MeetingSidebar` infer host state and call SDK functions directly.
  Rejected because it couples presentation to API clients and reduces
  testability.

## Risks / Trade-offs

- [Generated SDK output changes unrelated files] → Regenerate once, review the
  diff, and constrain implementation to the required moderation operations and
  any codegen fallout.
- [Identity mismatch between `hostId` and LiveKit participant identity hides or
  shows controls incorrectly] → Verify current meeting-room identity mapping and
  add a test scenario covering host-row suppression.
- [No optimistic updates can make moderation feel slower on high latency] → Use
  immediate loading indicators for button presses and rely on LiveKit state
  propagation for correctness.
- [Multiple simultaneous row actions can create confusing loading state] → Track
  loading per participant and per source so only the active button is replaced
  with a spinner.
- [Mute-all success can be ambiguous if some participants were already muted] →
  Treat HTTP 204 as authoritative success and show a transient “All Muted” state
  without trying to infer per-user deltas.

## Migration Plan

1. Regenerate the web SDK from the current OpenAPI spec and confirm the
   moderation endpoints are exported.
2. Update meeting-room types and container wiring to pass host identity, meeting
   id, and moderation callbacks into the sidebar.
3. Implement the host-only People tab controls, localized copy, and async
   feedback states.
4. Add or update tests for rendering rules and moderation action state handling.
5. Validate host and non-host meeting-room flows locally, especially host-row
   suppression and reactive mute updates.

Rollback is straightforward: revert the generated SDK diff and the meeting-room
UI wiring, which returns the sidebar to read-only participant display without
backend impact.

## Open Questions

- None for implementation scope; the host-resolution strategy will use existing
  `hostId` unless code inspection during implementation reveals a mismatch with
  LiveKit participant identities.
