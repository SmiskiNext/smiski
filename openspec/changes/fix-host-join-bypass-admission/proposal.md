## Why

After the `meeting-host-implicit-start` refactor, the host can no longer enter
their own meeting. Two regressions stack up: backend `RequestJoinUseCase`
elevates `SCHEDULED → LIVE` for the host but still routes the host through the
`MANUAL_APPROVAL` waiting-room branch (host is the only approver, so the request
hangs as `PENDING` forever); the web `EventSource` that listens for the approval
event uses a relative URL, which the browser resolves against the Next.js dev
origin (`localhost:3000`) instead of the API gateway
(`NEXT_PUBLIC_API_BASE_URL`, e.g. `localhost:30000`), so the SSE connection
retries until it gives up. Default settings in both the backend and web UI ship
with `MANUAL_APPROVAL` enabled, so every fresh instant or scheduled meeting hits
the bug.

## What Changes

- Backend `RequestJoinUseCase` SHALL fast-path the host to an approved join with
  a LiveKit token regardless of the meeting's `admissionPolicy`. The existing
  implicit `SCHEDULED → LIVE` elevation, the row-level lock, and the
  password-check skip for the host SHALL all continue to apply. Host requests on
  `ENDED` or `CANCELLED` meetings SHALL still fail with
  `INVALID_STATUS_TRANSITION`.
- Pending join requests submitted by other users SHALL NOT be auto-approved as a
  side effect of the host's join — they keep waiting for the host to approve
  them once the host is in the room.
- Web `frontends/web/src/lib/api/client.ts` SHALL expose `getApiBaseUrl()` as
  the single source of truth for the API gateway base URL (the same value passed
  into `configureApiClient`).
- Web `EventSource` URLs in `use-join-meeting.ts` and `use-waiting-room.ts`
  SHALL be built from `getApiBaseUrl()` so SSE traffic targets the API gateway
  instead of the Next.js dev origin.

## Capabilities

### New Capabilities

<!-- None — both touched capabilities already exist. -->

### Modified Capabilities

- `meeting-host-implicit-start`: extend the host implicit-start contract so the
  host always receives an approved join response with a LiveKit token, including
  when admission policy is `MANUAL_APPROVAL`.
- `web-join-meeting`: require the join-request SSE stream to use the configured
  API gateway base URL rather than relying on relative URLs.

## Impact

- Affected services: `services/meeting-management` (use case logic and tests).
- Affected clients: `frontends/web` — `src/lib/api/client.ts`,
  `src/components/join-meeting/use-join-meeting.ts`,
  `src/hooks/use-waiting-room.ts`.
- Out of scope: Android source, domain `Meeting.start()`,
  `MeetingStatus.canTransitionTo`, Flyway schema, password rules, admission
  policy semantics for non-hosts, Next.js rewrites, OpenAPI surface.
- Event publication unchanged — `MeetingStartedEvent` still emits exactly once
  per actual `SCHEDULED → LIVE` transition.
