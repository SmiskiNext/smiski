# Context

The current web meeting experience ends at a presentational shell with static
participant tiles, which is sufficient for validating layout direction but not
for delivering a usable meeting product. The backend already provisions LiveKit
room access during join flows, stores meeting credentials on the web client,
exposes generated SDK functions for waiting-room and recording operations, and
streams host events over SSE. The Android app already implements the target
experience with a view-model-driven architecture, so the web implementation can
align to the same product behavior while using React hooks and LiveKit React
primitives.

This change crosses multiple frontend modules: the meeting container,
participant rendering, toolbar, sidebar behavior, app header, new waiting-room
management flows, and new meeting-specific hooks. It also introduces an external
dependency on LiveKit's web SDK, which makes a design document useful before
implementation.

## Goals / Non-Goals

**Goals:**

- Connect the web meeting room to LiveKit using the existing session-scoped room
  name and token established by join/create flows.
- Replace static participant mockups with live participant media, camera-off
  fallbacks, speaking indicators, and responsive multi-participant layouts.
- Provide a meeting shell that surfaces connection state, reconnection
  messaging, and elapsed call time in a way that matches the backend and Android
  mental model.
- Give hosts actionable waiting-room controls backed by the existing
  join-request APIs and SSE event stream.
- Redesign meeting controls into a compact floating toolbar while preserving
  existing chat and settings functionality.
- Keep all user-visible text translatable through next-intl keys and keep
  styling aligned with the existing web design tokens.

**Non-Goals:**

- Implement draggable or resizable self-view picture-in-picture behavior.
- Redesign the backend LiveKit token lifecycle, room creation logic, or SSE
  protocol.
- Build new chat semantics or replace the current meeting settings feature set.
- Add advanced conferencing features beyond the requested scope, such as
  breakout rooms, network statistics panels, raised hands, or participant
  moderation beyond waiting-room approval/denial.

## Decisions

### 1. Use LiveKit React as the room state source and keep presentation components custom

The meeting container will mount a `LiveKitRoom` provider using the token and
room name already persisted in session storage by the web join flow. Participant
state, room connection state, and active-speaker state will come from LiveKit
React hooks such as `useParticipants`, `useTracks`, and `useConnectionState`.

This keeps the data plane aligned with the backend and Android implementation
while avoiding a second client-side synchronization layer. The UI will remain
custom Tailwind and shadcn components rather than adopting LiveKit's prebuilt
visual components so the product can preserve the existing design language and
meeting-specific composition.

Alternatives considered:

- Build directly on `livekit-client` without React bindings. Rejected because it
  would require more custom subscription and cleanup code for the same state.
- Use LiveKit prebuilt room UI. Rejected because it would not match the existing
  shell, toolbar, and layout requirements.

### 2. Separate room orchestration, layout state, and waiting-room state into dedicated hooks

The web equivalent of the Android view-model architecture will be lightweight
React hooks:

- `useMeetingLayout()` owns layout mode selection and participant pinning or
  spotlight decisions.
- `useCallTimer()` owns elapsed-duration formatting inputs.
- `useWaitingRoom(meetingId)` owns host-only join-request fetching, optimistic
  action state, and SSE subscription recovery.

This keeps `MeetingContainer` responsible for composition rather than business
state. It also makes layout rules, timer behavior, and waiting-room logic
independently testable.

Alternatives considered:

- Keep all state in `MeetingContainer`. Rejected because the component would
  become difficult to reason about as LiveKit, waiting room, toolbar, and
  responsive shell logic accumulate.
- Introduce a global store. Rejected because the requested scope is localized to
  the meeting route and does not require cross-page persistence.

### 3. Model participant rendering around normalized tile view models

Participant UI will be fed by normalized view models derived from LiveKit
participants and track publications rather than by raw SDK objects throughout
the tree. Each tile will receive the minimum render contract needed for UI:
participant identity, display name, video track reference, whether camera is
enabled, whether the participant is local, and whether the participant is
actively speaking.

This reduces coupling between presentational components and LiveKit internals,
makes layout composition easier, and allows spotlight/sidebar logic to choose
tile ordering without duplicating SDK-specific checks.

Alternatives considered:

- Pass full LiveKit participant objects everywhere. Rejected because it spreads
  SDK knowledge across too many components and makes testing harder.

### 4. Use deterministic layout rules with responsive caps rather than auto-measured tile algorithms

The layout system will support four user-selectable modes:

- Auto: derive columns from participant count, capped at two columns below 768px
  and one below 480px.
- Tiled: fixed two-column grid where the viewport allows it.
- Spotlight: one promoted participant and a thumbnail strip for the remaining
  participants.
- Sidebar: one promoted participant consuming roughly two-thirds width with a
  secondary grid for the rest.

Auto mode will follow the specified participant-count mapping. Spotlight and
sidebar modes will use the active speaker when no manual pin exists, with the
local participant excluded from the promoted region when a remote participant is
available. The self-view remains a separate bottom-right overlay in the main
grid region for V1, rather than being treated as a regular tile in every layout.

Alternatives considered:

- Let CSS auto-fit determine columns entirely. Rejected because the product
  needs predictable parity with the requested mapping and mobile caps.
- Treat self-view as a normal participant tile. Rejected because the target UX
  explicitly calls for floating picture-in-picture.

### 5. Keep host waiting-room updates API-driven with SSE as a freshness trigger

`useWaitingRoom(meetingId)` will load the authoritative pending list from
`listJoinRequests()` and mutate it through `approveJoinRequest()`,
`denyJoinRequest()`, and `approveAllJoinRequests()`. The SSE stream from
`subscribeToEvents()` will not attempt to fully model every state transition
locally; instead, it will trigger targeted list updates or a refetch when
join-request events arrive or when the stream reconnects.

This approach is more resilient than maintaining a purely event-sourced client
cache because approval and denial actions can race with other hosts or expiry
behavior. The list endpoint remains the source of truth, while SSE preserves
responsiveness.

Alternatives considered:

- Update the local list only from events. Rejected because dropped or delayed
  events could leave the UI stale.
- Poll on an interval. Rejected because SSE already exists and gives better
  responsiveness with less unnecessary traffic.

### 6. Drive connection feedback from LiveKit room state, not inferred network heuristics

The meeting header connection indicator will be keyed directly off
`useConnectionState()`. Connected, reconnecting, and disconnected states will
map to the requested visual tokens and accessible status messaging. A
lightweight inline reconnecting banner will appear in the room shell whenever
the state is reconnecting.

This keeps UI semantics aligned with the actual media-session state managed by
LiveKit instead of guessing from browser network events.

Alternatives considered:

- Use `navigator.onLine` or custom ping checks. Rejected because they do not
  represent LiveKit media connectivity accurately.

### 7. Preserve existing shell capabilities by composing them around the live room instead of replacing them

Chat, meeting settings, and sidebar content will remain in the current component
hierarchy, but their container behavior will adapt for narrower screens. The
sidebar becomes collapsible or drawer-like below the desktop breakpoint so the
meeting grid keeps priority on medium and small displays. The toolbar redesign
will centralize primary media controls, the host-only recording action, the
layout picker, and the leave dialog trigger without removing existing secondary
experiences.

Alternatives considered:

- Rebuild the entire meeting screen from scratch. Rejected because the existing
  shell already contains useful chat and settings integration that should be
  preserved.

## Risks / Trade-offs

- [Browser autoplay restrictions can prevent remote or local media from playing
  immediately] → Mitigation: use LiveKit's recommended video/audio track
  attachment patterns, keep remote video muted, enable `playsInline`, and ensure
  the join flow enters the meeting through an explicit user action.
- [Session storage credentials may be absent or stale when the meeting route
  renders] → Mitigation: treat missing room name or token as a guarded error
  path that redirects or shows a recoverable failure state instead of mounting
  the room with invalid data.
- [Waiting-room SSE streams can disconnect or deliver duplicate events] →
  Mitigation: make list fetching authoritative, reconnect the stream, and
  de-duplicate or safely refetch after relevant events.
- [Spotlight/sidebar promotion based on active speaker can feel unstable in
  noisy rooms] → Mitigation: keep a pinned participant override in layout state
  and fall back to stable ordering when there is no clear active speaker.
- [Rendering many video tiles can strain lower-powered devices] → Mitigation:
  use deterministic layouts, cap mobile columns, keep self-view separate, and
  avoid unnecessary re-renders by normalizing participant view models.
- [Host-only controls may appear incorrectly if role or meeting settings are not
  resolved early enough] → Mitigation: derive host visibility from existing
  meeting metadata and gate waiting-room and recording controls behind resolved
  host eligibility checks.

## Migration Plan

1. Add the LiveKit web dependencies to the web frontend package and ensure the
   existing build pipeline resolves them.
2. Introduce the new meeting hooks and presentational components behind the
   existing meeting route so navigation and auth flows remain unchanged.
3. Replace mock participant rendering with LiveKit-backed rendering while
   preserving chat and settings behavior.
4. Enable host-only waiting-room and recording controls using the generated SDK
   clients and SSE subscription.
5. Validate responsive layouts, connection-state handling, and leave/disconnect
   flows before release.
6. Rollback strategy: revert the meeting-room component changes and dependency
   additions, which returns the web client to the previous static shell without
   backend migration requirements.

## Open Questions

- Whether the meeting route already has a canonical source for host-role
  determination, or whether `getMeeting()` must be read on entry to resolve
  host-only controls consistently.
- Whether screen sharing is already partially implemented in the current toolbar
  logic or must be specified as a future follow-up beyond the current shell
  redesign.
- Whether recording state needs an explicit initial fetch from meeting details
  or can be handled entirely by the existing controls in this iteration.
