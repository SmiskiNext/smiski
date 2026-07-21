# Smiski for Jira

Smiski is an online meeting experience embedded in Jira Cloud through Atlassian Forge. It gives
teams issue-scoped meeting controls, a project-wide meeting dashboard, and an in-product video
room without requiring users to leave Jira.

> [!IMPORTANT]
> This repository is currently a frontend-focused prototype. Meeting data, participants,
> permissions, and recordings are backed by local mocks. The API layer and most Forge resolver
> operations are intentionally defined as typed integration seams for a future backend. Do not
> treat the current permission checks or meeting mutations as production security controls.

## Current capabilities

### Jira issue context

- Displays meetings linked to the current Jira issue.
- Searches meetings by title and filters them by lifecycle status.
- Starts instant meetings and schedules meetings for a later time.
- Supports view, edit, start, join, cancel, and end actions according to the frontend meeting
  policy.
- Opens the meeting room through the project page, using a small cross-module handoff.
- Uses a Forge platform modal for schedule/edit forms in Jira and an in-page modal during local
  development.

### Jira project page

- Displays a project-wide meeting dashboard.
- Filters meetings by title, issue, creator, and status.
- Provides sortable columns, pagination, meeting details, participant details, and lifecycle
  history.
- Supports instant and scheduled meeting creation.
- Hosts the meeting room used for both starting and joining calls.

### Meeting room

- Connects to LiveKit when the app runs inside Forge and valid LiveKit configuration is present.
- Publishes and renders camera and microphone tracks.
- Supports microphone, camera, screen-sharing, participant-list, and leave controls.
- Uses an adaptive video grid with a graceful mock-only fallback in standalone development.

### Jira integration

- Both Jira surfaces are served from one Custom UI bundle.
- Runtime surface selection is based on the Forge module context.
- Jira issue search uses `requestJira` as the invoking user and is the only production data query
  currently implemented in the frontend.
- Light and dark themes are derived from the Forge context, with system-theme fallback locally.

## Implementation status

| Area                  | Status                 | Notes                                                                                                      |
| --------------------- | ---------------------- | ---------------------------------------------------------------------------------------------------------- |
| Issue context UI      | Implemented with mocks | Issue-scoped list, filters, actions, and forms are functional.                                             |
| Project dashboard     | Implemented with mocks | Search, filters, sorting, pagination, details, and lifecycle actions are functional.                       |
| Meeting persistence   | Mocked                 | Meetings and participant rosters are stored in memory and mirrored to `localStorage`.                      |
| Jira issue lookup     | Implemented            | Calls Jira REST API through `@forge/bridge` in a real Forge context; uses fixtures in Vite development.    |
| Permission resolution | Mocked                 | The frontend models `Edit Meeting` as including `View Meeting`; no real Jira permission lookup exists yet. |
| Kong/backend API      | Stubbed                | Typed clients exist, but meeting, participant, and recording calls are not connected.                      |
| LiveKit room          | Prototype              | The client connection is implemented; a temporary Forge resolver mints room tokens directly.               |
| Recording             | Mocked                 | Types, hooks, and mock operations exist; no recording service is connected.                                |
| Automated tests       | Partial                | Pure permission-policy and issue-list filtering behavior are covered with Vitest.                          |

## Architecture

The repository is a pnpm workspace with two packages:

- The root package contains the Forge manifest and the thin resolver entry point.
- `static/smiski-ui` contains the Vite, React, and TypeScript Custom UI application.

The `jira:issueContext` and `jira:projectPage` modules both reference the same compiled resource.
`App.tsx` reads `context.moduleKey` and mounts the appropriate feature root.

```text
Jira issue context / project page
               │
               ▼
        App surface selection
               │
       ┌───────┴────────┐
       ▼                ▼
 Issue feature     Project feature
       └───────┬────────┘
               ▼
      Hooks + TanStack Query
               │
       ┌───────┴──────────┐
       ▼                  ▼
 Local mock store     Typed API layer
   (current)          (backend seam)
```

### Frontend layers

```text
static/smiski-ui/src/
├── api/                  # Typed backend clients and the real Jira issue query
├── components/
│   ├── shared/           # Reusable meeting components shared by both surfaces
│   └── ui/               # Local visual primitives and interaction helpers
├── context/              # Cross-cutting current-user state
├── domain/               # Domain types, enums, and pure meeting policy
├── features/
│   ├── issue-panel/      # Issue-scoped meeting experience
│   ├── project-page/     # Dashboard and meeting-room experiences
│   └── shared/           # Cross-surface Forge modal entry points
├── hooks/                # Query, mutation, navigation, and LiveKit adapters
├── mocks/                # Frontend-only data fixtures and mock database
├── theme/                # Jira/system color-mode integration
└── utils/                # Date-time and cross-module handoff utilities
```

The separation between `domain`, `api`, `hooks`, and feature components is deliberate. Consumers
depend on hook-level contracts, while the hooks currently call functions with signatures matching
the future API clients. Replacing the mock data source should therefore require minimal UI changes.

### Data flow today

- Meeting reads and writes use `mocks/db.ts` through TanStack Query hooks.
- Meeting and participant changes persist to browser `localStorage` on a best-effort basis.
- Recording changes are held in memory for the current page session.
- Project issue search uses local fixtures in `pnpm ui:dev` and Jira REST API in Forge.
- The current Jira user and project permissions are mocked.
- LiveKit is disabled in standalone Vite development because the Forge bridge is unavailable.

### Planned backend boundary

The intended production design keeps business logic outside the Forge app. The Forge resolver
should remain a thin identity-aware bridge to services exposed through Kong Gateway, while the
backend enforces authorization, lifecycle rules, tenant isolation, and persistence.

The main integration points are:

1. Implement the common request/authentication flow in `static/smiski-ui/src/api/client.ts` or in
   thin Forge resolver functions, depending on the final identity bridge.
2. Implement the typed resource clients in `static/smiski-ui/src/api/`.
3. Replace mock query and mutation functions in `static/smiski-ui/src/hooks/` with those clients.
4. Resolve real Jira user identity, project membership, and `View Meeting` / `Edit Meeting`
   permissions.
5. Move LiveKit token issuance to the meeting service and authorize every token request against
   the meeting and participant roster.
6. Replace placeholder Kong origins and review the minimum required Forge scopes and egress rules.

See [architecture.vi.md](architecture.vi.md) for the broader system design and
[PERMISSION.md](PERMISSION.md) for the meeting authorization model.

## Technology stack

- Atlassian Forge Custom UI
- React 18 and TypeScript
- Vite 5
- Tailwind CSS 4
- TanStack Query 5
- LiveKit client and server SDKs
- Vitest
- ESLint and Prettier
- pnpm workspaces

## Prerequisites

- Node.js 22 or later
- pnpm 10
- An Atlassian account with Forge access for Jira testing and deployment
- Forge CLI authentication for tunnel, deploy, install, variables, and logs commands
- LiveKit project credentials for real video-room testing inside Forge

## Local development

Install all workspace dependencies from the repository root:

```bash
pnpm install
```

Start the standalone frontend:

```bash
pnpm ui:dev
```

Vite development mode does not have access to Forge context or bridge operations. The application
therefore displays a development switcher that can preview the issue context and project page with
mock data. LiveKit networking is disabled, but the room layout and local control states remain
available for UI development.

Mock meetings and participant selections may persist between reloads in `localStorage`. Clear the
site data for the Vite origin to restore the initial fixtures.

## Available commands

Run commands from the repository root unless stated otherwise.

| Command             | Purpose                                                                 |
| ------------------- | ----------------------------------------------------------------------- |
| `pnpm ui:dev`       | Start the standalone Vite development server.                           |
| `pnpm build`        | Type-check and build the Custom UI bundle into `static/smiski-ui/dist`. |
| `pnpm test`         | Run the Vitest test suite once.                                         |
| `pnpm lint`         | Lint the resolver and frontend workspaces.                              |
| `pnpm typecheck`    | Type-check both workspaces without emitting files.                      |
| `pnpm format`       | Format the repository with Prettier.                                    |
| `pnpm format:check` | Verify formatting without modifying files.                              |

To run a single test file:

```bash
pnpm --filter smiski-ui exec vitest run src/domain/meetingPolicy.test.ts
```

## Running inside Forge

Build the frontend before deploying because `manifest.yml` points to the generated `dist`
directory:

```bash
pnpm build
pnpm exec forge lint
pnpm exec forge deploy --non-interactive -e development
```

Install the development deployment on a Jira site:

```bash
pnpm exec forge install --non-interactive --site your-site.atlassian.net --product jira --environment development
```

Use the `--upgrade` option when an existing installation must receive updated scopes or egress
permissions.

### LiveKit configuration

The temporary `getRoomToken` resolver reads these Forge environment variables:

- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`
- `LIVEKIT_URL`

Store credentials as Forge environment variables for the target environment; never commit them.
The LiveKit WebSocket origin must also be permitted under `permissions.external.fetch.client` in
`manifest.yml`.

> [!WARNING]
> The current resolver accepts any non-empty meeting ID from a user who can invoke the app and does
> not verify meeting membership or permission before minting a token. This implementation is for
> prototype testing only and must be replaced before production use.

## Forge manifest notes

The manifest currently declares:

- `jira:issueContext` with module key `smiski-issue-context`
- `jira:projectPage` with module key `smiski-project-page`
- one shared Custom UI resource at `static/smiski-ui/dist`
- the `read:jira-work` scope for Jira issue search
- placeholder Kong Gateway egress origins
- a LiveKit client WebSocket origin

Replace all placeholder gateway origins before deployment to a real environment. After adding or
changing scopes or external egress permissions, deploy again and upgrade the existing Jira
installation.

## Testing and quality checks

Before opening a pull request, run:

```bash
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec forge lint
```

Current tests cover the permission/action matrix and issue-context list filtering and ordering.
Integration tests for Forge context, Jira REST calls, backend contracts, and LiveKit behavior remain
to be added.

## Production-readiness checklist

Before a production release:

- Connect all meeting, participant, permission, and recording operations to the backend.
- Enforce authorization and meeting-state transitions on the backend for every mutation.
- Replace the direct LiveKit token-minting shim with an authorized backend endpoint.
- Replace mock current-user and project-member data with tenant-aware identities.
- Configure real Kong Gateway origins and remove placeholder egress entries.
- Review and minimize Forge scopes and external permissions.
- Add contract, integration, and end-to-end coverage for both Jira modules.
- Define operational logging, monitoring, error handling, and recovery behavior.
- Perform accessibility, browser, and Jira light/dark-theme validation.

## Additional documentation

- [System architecture (Vietnamese)](architecture.vi.md)
- [Meeting permission model](PERMISSION.md)
- [Broader Smiski project notes](DOCS1.md)
