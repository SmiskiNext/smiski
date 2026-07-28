## Why

The Forge app can start an instant meeting, but its invitee picker only offers
project-assignable users and the create flow still runs against the in-memory
mock. Users need to invite anyone in the Jira site (workspace users) and have
"start now" actually create the meeting through the real `meet` backend. This
change also standardizes the create-instant UI on Ant Design (the project's
declared component library) and a shadcn-style `cn` utility.

## What Changes

- Add a workspace-user invite picker (Ant Design `Select` multiple with
  server-side typeahead). Suggestions come from all Jira site users, fetched in
  the Forge resolver via `@smiskinext/sdks-jira` running as the invoking user
  (`.asUser()`). Empty query seeds via `getAllUsers`; a typed query uses
  `findUsers`. Results are filtered to active `atlassian` accounts.
- Wire "create instant meeting" to the real backend: the resolver calls
  `@smiskinext/smiski-ts` `createInstant`
  (`POST /api/{version}/meetings:instant`), forwarding to the Caddy gateway with
  `X-Account-Id` and `X-Tenant-ID` from the Forge invocation context.
  **BREAKING**: the instant-create flow no longer falls back to the mock
  database; it requires a reachable backend (via Forge tunnel/deploy).
  Standalone `vite dev` cannot create instant meetings.
- Rewrite the shared `StartInstantMeetingModal` entirely with Ant Design
  (`Modal`, `Form`, `Input`, `Select`), used by both the project-page dashboard
  and the Issue Panel. The Issue Panel "Start instant" button now opens this
  modal (after the existing host-conflict gate) instead of creating immediately.
- Convert `IssuePicker` internals to Ant Design `Select` (search enabled) while
  keeping its props, so `ScheduleMeetingModal` inherits the change.
- Upgrade `cn` to the shadcn standard (`twMerge(clsx(...))`); components remain
  Ant Design, not shadcn/ui.
- Add Ant Design theming via `ConfigProvider` (brand token + dark algorithm
  driven by the existing color mode).
- **BREAKING**: add `permissions.content.styles: ['unsafe-inline']` to
  `manifest.yml` so Ant Design's runtime CSS-in-JS renders under Forge CSP. This
  is a major-version upgrade requiring `forge deploy` +
  `forge install --upgrade` and may affect "Runs on Atlassian" eligibility.

## Capabilities

### New Capabilities

- `ui-design`: Front-end presentation standards for the create-instant surfaces
  — Ant Design component usage, theming/dark-mode alignment through
  `ConfigProvider`, the shadcn-style `cn` utility, the workspace-user invite
  picker behavior and its loading/empty/error/permission states, and the Forge
  CSP allowance required to render Ant Design.
- `ui-backend-interaction`: How the Forge app talks to Jira and the `meet`
  backend from the resolver — workspace-user search through
  `@smiskinext/sdks-jira` (`.asUser()`), instant-meeting creation through
  `@smiskinext/smiski-ts` (`createInstant`) forwarded to the Caddy gateway with
  tenant/account headers, and the frontend `invoke` boundary plus its error
  handling.

### Modified Capabilities

- `create-instant-meeting`: The client contract is refined so the app now
  sources invitees from workspace users (each carrying `email`, `accountId`,
  `displayName`) and creates instant meetings against the real backend rather
  than the mock, from both the dashboard and the Issue Panel.

## Impact

- **App frontend (`app/static/smiski-ui`)**: new `WorkspaceUserPicker`,
  rewritten `StartInstantMeetingModal`, Ant Design `IssuePicker`, `cn` utility,
  `App.tsx` `ConfigProvider`, new `useWorkspaceUsers` hook +
  `api/workspaceUsers` + `mocks/workspaceUsers`, updated instant-create wiring
  in `api/meetings.ts` and `useMeetingMutations`, Issue Panel modal plumbing.
- **App resolver (`app/src`)**: new `searchWorkspaceUsers` resolver + Jira SDK
  client adapter; implemented `createInstantMeeting` resolver + `meet` SDK
  client adapter.
- **SDK packaging**: `@smiskinext/smiski-ts` (workspace package
  `sdks/typescript`) becomes a dependency of `app` and must be built before the
  resolver imports it.
- **Dependencies**: add `antd`, `@ant-design/icons`, `clsx`, `tailwind-merge`.
- **Manifest / deployment**: `content.styles: ['unsafe-inline']`; requires
  redeploy + reinstall (major upgrade). No new scopes (`read:jira-user` already
  granted). No new egress beyond the existing Caddy gateway origin.
- **Out of scope**: `meet` backend service code, scheduled/list/update/delete
  flows, the dashboard "creator" filter, and `useMeetingParticipants`.
