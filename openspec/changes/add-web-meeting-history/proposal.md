## Why

The web client's `/[locale]/workspace/history` route currently renders a static
"coming soon" placeholder. Authenticated users on the web cannot review their
past meetings, view participants, or play back recordings — capabilities that
are already first-class on the Android app via the same backend API
(`listParticipatedMeetings` and `getParticipatedMeetingDetail`). This change
brings the web client to parity with Android so users can access their meeting
history regardless of platform.

## What Changes

- Replace the placeholder `MeetingHistoryScreen` with a real list view that
  fetches meetings filtered to `ENDED` and `CANCELLED` status from
  `listParticipatedMeetings`.
- Add an explicit "Refresh" button and a "Load more" button (web-native
  pagination instead of infinite scroll) at the section header / list footer.
- Render cancelled meetings with strikethrough title, 0.7 opacity, and a red
  "CANCELLED" badge — mirroring the Android visual treatment.
- Add a new dynamic route `/[locale]/workspace/history/[meetingId]` that fetches
  full meeting detail (participants + recordings) via
  `getParticipatedMeetingDetail`.
- Render the detail screen with title, type/status badges, full date, time range
  and duration, optional description, participants preview (first 5 with "Show N
  more" expand), and recordings list.
- Open recordings in an inline HTML5 `<video controls>` overlay player with
  error and retry states.
- Add a third "History" navigation item to `WorkspaceShell` alongside Home and
  Schedule, so the feature is discoverable from the primary nav bar.
- Extend i18n messages (`workspace.history.*` and `workspace.common.navHistory`)
  for English and Vietnamese.
- Show a sonner toast on `loadMore` failure with a retry action; keep the
  current list visible.

## Capabilities

### New Capabilities

- `web-meeting-history-list`: Web client list screen for the authenticated
  user's past meetings (ENDED / CANCELLED), with refresh and load-more
  pagination, plus loading / empty / error states and a workspace-nav entry.
- `web-meeting-detail-view`: Web client detail screen for a single past meeting,
  showing metadata, description, participants preview, and recordings, reached
  via `/[locale]/workspace/history/[meetingId]`.
- `web-recording-playback`: Web client inline HTML5 video player overlay that
  plays a meeting recording on demand, with error and retry states.

### Modified Capabilities

(none — no existing web capability owns these requirements yet)

## Impact

- **Web client (`frontends/web/`)**: new components under
  `src/components/meeting-history/`, new dynamic route under
  `src/app/[locale]/workspace/history/[meetingId]/page.tsx`, modifications to
  `meeting-history-screen.tsx`, `workspace-shell.tsx`, and the `en.json` /
  `vi.json` message catalogs.
- **Generated SDK (`frontends/web/src/generated/`)**: no changes — both
  endpoints (`listParticipatedMeetings`, `getParticipatedMeetingDetail`) and
  their response types are already generated.
- **Backend services**: no changes — endpoints already exist and are consumed
  unchanged by the Android client.
- **OpenAPI spec / SDK regeneration**: not required.
- **Tests**: new Vitest unit tests for the list hook (state machine and
  pagination merge) and a component test for the meeting history card (cancelled
  visual + badges).
