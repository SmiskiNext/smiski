# Why

Web hosts can already create, join, and launch meetings, but they still cannot
update meeting settings from the web experience even though the backend
replacement API is already available. The current home screen also relies on
mock upcoming-meeting data, which blocks hosts from managing real scheduled
meetings and exposes an admission-policy mismatch that causes invalid settings
requests.

## What Changes

- Add a web meeting-settings editing flow that loads current settings from the
  backend, maps them into the existing settings form, and saves updates through
  `PUT /api/v1/meetings/{id}/settings`.
- Expose meeting-settings entry points from the in-room toolbar and the
  workspace home screen so hosts can update live or scheduled meetings without
  leaving the current surface.
- Replace mock upcoming-meeting content on the workspace home screen with real
  host meeting data from `listHostMeetings`, including loading, empty, and error
  states.
- Fix the web meeting settings `admissionPolicy` request mapping to use
  backend-supported values and add reverse mapping from response payloads into
  form values.
- Extend the meeting-room credential handoff to persist the backend meeting
  identifier alongside the room token and room name so the room UI can open the
  settings dialog for the active meeting.
- Add English and Vietnamese copy for the new settings-management and
  host-meeting-loading states.

## Capabilities

### New Capabilities

- `web-meeting-settings-management`: Let web hosts load, edit, and save meeting
  settings from the meeting room and workspace home using the backend meeting
  settings API and real host meeting data.

### Modified Capabilities

- `web-join-meeting`: Approved join and instant-meeting handoff state also
  preserves the resolved meeting identifier so downstream meeting-room features
  can target the active meeting resource.

## Impact

- Affected frontend code in `frontends/web/src/components/meeting`,
  `frontends/web/src/components/create-meeting`,
  `frontends/web/src/components/join-meeting`,
  `frontends/web/src/components/workspace-home-screen.tsx`, and
  `frontends/web/src/lib/schemas/meeting.ts`.
- Uses existing generated web SDK operations `getMeeting`, `putMeetingSettings`,
  and `listHostMeetings` plus existing meeting settings form infrastructure.
- Updates localized message bundles in `frontends/web/src/messages/en.json` and
  `frontends/web/src/messages/vi.json`.
- No backend API changes are required; this change consumes the existing meeting
  settings replacement API contract.
