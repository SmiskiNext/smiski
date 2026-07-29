## Why

The schedule-meeting flow in the Custom UI is still built on the legacy custom
component kit and persists only to the in-memory mock, while the instant-meeting
flow already runs on Ant Design and calls the real `meet` backend through Forge
Remote. This leaves scheduling unable to satisfy the `create-schedule-meeting`
backend contract (real `SCHEDULED` meetings, hashed invite tokens,
host-from-header identity) and inconsistent with the shipped instant experience.
Aligning it now closes that gap before scheduling is exercised end-to-end.

## What Changes

- Rebuild the schedule-meeting form with Ant Design (`Form`, `Input`,
  `DatePicker`, `TimePicker`, `Select`, `Modal`, `Alert`), mirroring the
  instant-meeting modal, replacing the legacy `components/ui` kit and native
  `<form>`/`<input>` controls.
- Add an explicit **end time** control alongside start time, validating that
  start is before end and start is not in the past (in the chosen zone).
- Source invitees from the workspace-user picker so each carries a real
  `accountId`, `displayName`, and `email`, replacing the synthesized-email
  `participantAccountIds` path.
- Wire the create-schedule action to the real `meet` backend via Forge Remote
  (`POST /api/1/meetings:schedule`), reusing the instant flow's FIT-only auth
  and problem+json error handling. The create flow no longer falls back to the
  mock. **BREAKING**: standalone `vite dev` can no longer create scheduled
  meetings (same tradeoff already accepted for instant).
- Default the meeting time zone from the invoking user's Jira profile (`/myself`
  → `timeZone`) for BOTH the schedule and instant flows, falling back to the
  browser zone only when the profile omits it.
- Keep the schedule payload free of any `host` object (host identity is resolved
  from the request header by the backend).
- Out of scope: the edit/update-meeting branch of the modal (stays on the mock),
  invitation-email delivery, and any `manifest.yml` change (schedule reuses the
  already-declared `meet-backend` remote).

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `ui-backend-interaction`: add scheduled-meeting creation through the `meet`
  backend via Forge Remote (FIT-only auth, no client-asserted identity, no mock
  fallback), and require the meeting time zone to be resolved from the invoking
  user's Jira profile for both instant and scheduled creation.
- `ui-design`: require the schedule-meeting surface to be built from Ant Design
  components (start/end/zone controls, workspace-user invite picker), matching
  the instant-meeting modal.

## Impact

- App frontend (`app/static/smiski-ui`):
    - `src/components/shared/ScheduleMeetingModal.tsx` (rebuild create branch)
    - `src/components/shared/StartInstantMeetingModal.tsx` (zone from profile)
    - `src/api/meetings.ts` (add pure `buildScheduleMeetingPayload`, rewrite
      `scheduleMeeting` to use Forge Remote; extend `ScheduleMeetingInput`
      invitees/endTime)
    - `src/hooks/useMeetingMutations.ts` (`useScheduleMeeting` → real backend)
    - `src/api/currentUser.ts`, `src/domain/projectMember.ts`,
      `src/mocks/users.ts`, `src/utils/datetime.ts` (carry + resolve profile
      time zone)
    - Tests: `src/api/*.test.ts` (schedule payload builder + Forge Remote call),
      `datetime`/`currentUser` zone tests; retire dead `scheduleMeetingRequest`
      mapper path if no longer referenced.
- No backend, manifest, or dependency changes. Depends on existing
  `create-schedule-meeting` backend spec and the `read:jira-user` scope already
  granted.
