# Smiski for Jira

Smiski is an online meeting experience embedded in Jira Cloud through Atlassian
Forge. It gives teams issue-scoped meeting controls, a project-wide meeting
dashboard, and an in-product video room without leaving Jira.

This app is the **Custom UI frontend only**. All meeting business logic,
persistence, authorization, and LiveKit token issuance live in the `meet`
backend (see [../services/README.md](../services/README.md)). The app reaches it
over Forge Remote and reaches Jira directly from the browser via
`@forge/bridge`. There is no Forge resolver/function and no mock database —
meeting data is never mocked.

## How it connects

Both Jira surfaces render the **same** Custom UI bundle. `App.tsx` reads
`view.getContext()` and branches on `context.moduleKey` to mount the right root.
The app only renders inside a real Forge module; without a Forge context it
shows an "unknown surface" state, so day-to-day development runs through
`forge tunnel`, not a standalone browser tab.

```text
Jira issue panel / project page (one Custom UI bundle)
                │  view.getContext() → moduleKey
        ┌───────┴────────┐
        ▼                ▼
  Issue feature     Project feature
        └───────┬────────┘
                ▼
      Hooks + TanStack Query
                │
     ┌──────────┼───────────────┐
     ▼          ▼               ▼
 Forge Remote   requestJira     raw fetch / WebSocket
 (meet backend) (Jira REST)     (SSE stream, LiveKit)
```

### Transports (`static/smiski-ui/src/api/`)

- **Backend meeting calls** go through `invokeRemote` via `forgeRemoteFetch.ts`,
  a `fetch`-shaped adapter injected into the generated `@smiskinext/smiski-ts`
  SDK. The Forge platform proxy attaches both the Forge Installation Token and
  the app system token (`x-forge-oauth-system`) the gateway requires. The
  adapter injects `x-issue-id` / `x-project-id` from `backendContext.ts` and
  asserts no tenant/account identity itself.
- **Jira-native calls** use `@forge/bridge`'s `requestJira` as the invoking user
  — either raw (`currentUser.ts`, `issues.ts`, `projectMembers.ts`) or through
  the `@smiskinext/sdks-jira` SDK via `jiraSdkFetch.ts` (`workspaceUsers.ts`,
  `meetingPermission.ts`).
- **Streaming** bypasses Forge Remote: the SSE join-request stream
  (`meetingEvents.ts`, `sseClient.ts`) uses a raw `fetch` to
  `apiConfig.apiBaseUrl`, and the LiveKit room dials its signalling origin
  directly. Both origins are declared under `permissions.external.fetch.client`
  in `manifest.yml`.

### Layering

`domain/` holds pure logic with colocated tests; `api/` holds transport
adapters; `hooks/` wraps TanStack Query (every cache key is registered in
`hooks/queryKeys.ts`); `components/ui/` holds local Tailwind primitives.

## Capabilities

### Jira issue panel

- Lists meetings linked to the current issue; searches by title and filters by
  lifecycle status.
- Starts instant meetings and schedules meetings for later.
- Supports view, edit, start, join, cancel, and end actions per the meeting
  permission policy.
- Opens the meeting room by handing off to the project page through
  `localStorage` (`utils/meetingRoomHandoff.ts`) — the two surfaces are separate
  iframes with no shared React tree.
- Opens schedule/edit forms as Forge platform modals (too wide for the panel
  iframe), falling back to in-page modals under the tunnel.

### Jira project page

- Project-wide meeting dashboard: filter by title, issue, creator, and status,
  with sortable columns and pagination.
- Meeting details, participant details, and lifecycle history.
- Instant and scheduled meeting creation.
- Hosts the meeting room used for both starting and joining calls.

### Meeting room

- Connects to LiveKit using a room token minted by the backend `meet` service's
  `join` operation. `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` are backend
  secrets, never Forge app variables.
- Publishes and renders camera and microphone tracks; supports mic, camera,
  screen-share, participant-list, and leave controls.
- Uses an adaptive video grid.

## Wiring status

Meeting reads, writes, join, and token issuance are real against the backend.
`getMeetingPermission` performs a real Jira permission check (see
[Permissions](#permissions)). Recording types and hooks exist but no recording
service is connected. Automated coverage focuses on pure permission-policy logic
and issue-list filtering; contract, Forge-context, and LiveKit integration tests
remain to be added.

## Permissions

`getMeetingPermission` checks the custom `View Meeting` / `Edit Meeting` project
permissions (declared as `jira:projectPermission` in `manifest.yml`) through
`GET /rest/api/3/mypermissions`. It first resolves the real permission keys from
`GET /rest/api/3/permissions`, because Jira does not echo back the bare manifest
key — the lookup matches on `name`. See [PERMISSION.md](PERMISSION.md) for the
full authorization model.

## Technology stack

- Atlassian Forge Custom UI
- React 18 + TypeScript
- Vite + Tailwind CSS 4 + Ant Design
- TanStack Query 5
- LiveKit client SDK
- Vitest + Biome
- pnpm workspaces

`app/` is its **own** pnpm workspace root, separate from the repo lockfile. Its
members are `.` (the manifest + Forge CLI, no code), `static/smiski-ui/` (the
Custom UI bundle), and `../sdks/typescript` (`@smiskinext/smiski-ts`) linked as
`workspace:*`.

## Prerequisites

- Node.js 22+ and pnpm 10
- An Atlassian account with Forge access, and Forge CLI authentication (for
  tunnel, deploy, install, variables, and logs)
- Auth for the private `@smiskinext` scope on GitHub Packages in `~/.npmrc` —
  `@smiskinext/sdks-jira` is private and `pnpm install` fails without it
- LiveKit project credentials on the backend for real video-room testing

## Local development

Run all commands from `app/`.

```bash
pnpm install

# @smiskinext/smiski-ts ships from a gitignored dist/ — build it first
pnpm --filter @smiskinext/smiski-ts build

pnpm ui:dev # Vite dev server on :5173 (reached through `forge tunnel`)
```

Because the app renders only inside a real Forge module, `pnpm ui:dev` is not a
standalone preview — point `forge tunnel` at it to develop against a live Jira
module with a real Forge bridge.

### Environment files

Both files below declare the **same** variable names. The Forge CLI interpolates
them into `manifest.yml`'s `${...}` placeholders, and `vite.config.ts` injects
them into the bundle — one value per concept, so the manifest's declared egress
and the origins the frontend calls cannot drift apart.

| Variable              | Meaning                                                    |
| --------------------- | ---------------------------------------------------------- |
| `SMISKI_API_BASE_URL` | Backend API gateway origin. Used by the manifest + bundle. |
| `LIVEKIT_URL`         | LiveKit signalling origin. Used by the manifest + bundle.  |
| `SMISKI_API_VERSION`  | API version path segment; defaults to `1` (`/api/1/...`).  |

```bash
# Forge CLI shell variables (auth + interpolation)
cp .env.example .env
set -a
. ./.env
set +a
pnpm exec forge lint

# Custom UI build variables
cp static/smiski-ui/.env.example static/smiski-ui/.env.local
```

Never commit `.env`, `.env.local`, or Forge API tokens. `LIVEKIT_API_KEY` /
`LIVEKIT_API_SECRET` are backend secrets and are deliberately absent here.

## Commands

Run from `app/` unless noted.

| Command             | Purpose                                                       |
| ------------------- | ------------------------------------------------------------- |
| `pnpm ui:dev`       | Start the Vite dev server (reached through `forge tunnel`).   |
| `pnpm build`        | Type-check and build the bundle into `static/smiski-ui/dist`. |
| `pnpm test`         | Run the Vitest suite once.                                    |
| `pnpm lint`         | Lint with Biome.                                              |
| `pnpm typecheck`    | Type-check the `smiski-ui` workspace.                         |
| `pnpm format`       | Format with Biome.                                            |
| `pnpm format:check` | Verify formatting without modifying files.                    |
| `pnpm run deploy`   | Build, then `forge deploy`.                                   |
| `pnpm run forge …`  | Pass through to the Forge CLI (e.g. `logs --since 15m`).      |

Use `pnpm run deploy`, never `pnpm deploy` — pnpm 10's built-in `deploy` shadows
the script, and `forge deploy` uploads `dist/` as-is without building it.

Single test, from `static/smiski-ui`:

```bash
pnpm exec vitest run src/domain/meetingPolicy.test.ts
```

There is no `vitest.config.ts`; DOM tests opt in per file with a
`// @vitest-environment jsdom` first-line pragma.

## Running inside Forge

Build before deploying — `manifest.yml` points at the generated `dist`:

```bash
pnpm build
pnpm exec forge lint
pnpm exec forge deploy --non-interactive -e development
pnpm exec forge install --non-interactive --site your-site.atlassian.net --product jira --environment development
```

After changing scopes or egress, deploy again **then** `forge install --upgrade`
— a tunnel restart is not enough, and administrators must re-consent.

### Continuous deployment

`.github/workflows/forge-deploy.yml` targets two Forge environments:

| Environment   | Trigger                           |
| ------------- | --------------------------------- |
| `development` | automatic on push to `dev`        |
| `staging`     | manual only (`workflow_dispatch`) |

Forge `production` is intentionally unused — it forbids `forge tunnel` and
`forge logs`, so `staging` is the release-grade, still-debuggable environment.
`main` does not deploy. Per-environment values come from GitHub Environment
`vars` / `secrets` and are validated before build.

## Manifest notes

The manifest declares `jira:issuePanel` (`smiski-issue-panel`) and
`jira:projectPage` (`smiski-project-page`) against one shared Custom UI
resource; the two custom `jira:projectPermission` entries; a Forge Remote
`endpoint` bound to the `meet-backend` remote (with the app system token
enabled); and `content.styles: [unsafe-inline]` required for Ant Design's
runtime CSS-in-JS. `@forge/cli` is pinned to `13.2.0` — 13.3.0+ rejects `${...}`
placeholders in egress-reachable fields before variable substitution.

## Additional documentation

- [Meeting permission model](PERMISSION.md)
- [Backend services](../services/README.md)
- [Repository overview](../README.md)

## License

GNU Affero General Public License v3.0 (AGPL-3.0) — see [LICENSE](../LICENSE)
for details. Copyright 2025 SmiskiNext.
