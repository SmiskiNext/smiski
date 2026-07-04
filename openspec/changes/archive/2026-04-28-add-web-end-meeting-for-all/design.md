# Context

The backend and generated web SDK already support ending a meeting for all
participants, and Android already exposes this host-only action from its active
call leave flow. The remaining gap is the web live meeting room, where the
current leave dialog only confirms a local disconnect and does not differentiate
host and non-host behavior.

This change is confined to the web frontend, but it crosses multiple frontend
concerns: meeting-room state, generated SDK usage, localized dialog copy, and
component tests. The existing meeting room already computes `isHost`, and the
leave dialog is only rendered from one call site, which keeps the integration
narrow.

## Goals / Non-Goals

**Goals:**

- Let web hosts choose between leaving locally and ending the meeting for all
  participants from the active meeting leave dialog.
- Preserve the current leave-only confirmation flow for non-host participants.
- Reuse the existing generated `endMeeting()` SDK function without introducing
  new frontend abstractions.
- Keep the dialog open during end-meeting submission, disable conflicting
  actions, and show inline localized error feedback on failure.
- Add focused unit test coverage for role-specific rendering and host
  loading-state behavior.

**Non-Goals:**

- Changing backend meeting termination behavior, authorization, or LiveKit
  teardown logic.
- Refactoring the broader meeting room architecture or introducing shared
  mutation hooks.
- Changing mute participant controls, waiting room behavior, or scheduled
  meeting cancellation flows.
- Adding new navigation destinations beyond the existing localized workspace
  route.

## Decisions

### Reuse the existing leave dialog component for both role flows

The leave dialog will remain the single exit surface, but it will accept
`isHost` and `meetingId` so it can branch between host and non-host actions
internally. This avoids creating a second host-only dialog and keeps toolbar
behavior unchanged.

Alternative considered:

- Create separate host and non-host dialog components. Rejected because the
  existing dialog has a single usage site, so splitting it would add indirection
  without meaningful reuse.

### Call the generated SDK directly from the dialog action

The host "End for All" button will call the generated
`endMeeting({ path: { id: meetingId } })` function directly in the dialog
component. This follows the existing project guidance for simple one-off actions
and keeps the implementation close to the user interaction that owns loading and
error state.

Alternative considered:

- Introduce a custom React hook or service wrapper. Rejected because the flow is
  small, localized to one component, and does not need reuse yet.

### Treat end-meeting as an in-dialog async mutation with recoverable failure

When a host chooses "End for All", the dialog will enter a submitting state that
disables actions and shows a spinner on the destructive button. If the API call
fails, the dialog stays open and renders an inline localized error so the host
can retry or fall back to a local leave.

Alternative considered:

- Close the dialog immediately and surface a toast on failure. Rejected because
  it weakens the confirmation flow, obscures retry intent, and is less
  consistent with the existing cancel-meeting dialog pattern.

### Keep post-success cleanup identical to the existing leave flow

After a successful end-meeting response, the frontend will disconnect from the
LiveKit room and navigate to `/${locale}/workspace`, matching the current leave
behavior except for the additional API call. This keeps success handling
predictable and aligned with Android behavior.

Alternative considered:

- Navigate first and let room teardown happen later. Rejected because explicit
  disconnect before navigation matches the current safe-exit contract and
  reduces the chance of stale in-room state.

### Extend the existing live meeting room capability instead of creating a new capability

This work changes exit behavior within the existing `web-live-meeting-room`
capability rather than introducing a separate capability. The feature is an
evolution of the meeting control bar and leave confirmation behavior already
covered by that spec.

Alternative considered:

- Define a new capability for host meeting termination on web. Rejected because
  the requirement is not standalone from the live meeting room experience.

## Risks / Trade-offs

- Host-only branching could accidentally regress the non-host leave flow ->
  Mitigation: preserve the existing non-host path unchanged and add explicit
  tests for both roles.
- Async submission could allow duplicate end requests or mixed actions if state
  handling is incomplete -> Mitigation: use a single loading flag to disable
  dialog actions while the end request is in flight.
- API failure messaging might be inconsistent across locales -> Mitigation: add
  dedicated translation keys in both supported message catalogs and render the
  error inline from localized copy.
- Direct SDK usage in the component increases UI-level coupling to the API call
  -> Mitigation: keep the call isolated to one component and avoid spreading the
  pattern unless reuse emerges later.

## Migration Plan

No backend or data migration is required. Deploy the web frontend change after
verifying the generated SDK already exposes `endMeeting()` in the current
branch. Rollback is a frontend-only revert that restores the previous leave-only
dialog.

## Open Questions

- None.
