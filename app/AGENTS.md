# AGENTS.md — Smiski Forge app

Guidance for agents working in `app/`. Keep it verifiable against config and
code, not prose. Root repo rules live in `../AGENTS.md`; this file owns the
Forge app only.

## What this is

The Atlassian **Forge Custom UI** app that embeds the Smiski online-meeting
module into Jira. It renders React UI inside Jira and reaches the backend only
through Forge Remote → the Caddy API gateway. It never touches Postgres, Kafka,
or LiveKit-server directly — the sole exception is the LiveKit **client** SDK
used inside the meeting room for WebRTC media.

This is **Custom UI, not UI Kit.** Any instruction telling you to use
`@forge/react`, `@forge/ui`, or a fixed UI Kit component list does not apply —
that is generic Forge boilerplate and is wrong for this repo. The UI is plain
React 18 + JSX (`<div>` etc.) + Tailwind v4 + Ant Design.

**Status:** scaffold. Most resolvers (`src/index.ts`) throw
`Not implemented: ...`, and most frontend reads run against in-memory mocks. Do
not assume a code path is wired to the backend — check the specific hook/
resolver first.

## Layout

Two pnpm workspace packages (`pnpm-workspace.yaml`):

- **root** (`.`) — the Forge app: `manifest.yml` + `src/` resolver (plain
  TypeScript, CommonJS, no framework). Owns the Forge CLI.
- **`static/smiski-ui/`** — the Custom UI frontend: Vite + React 18 + TS +
  Tailwind v4 + TanStack Query + Ant Design + LiveKit client. This is the _only_
  UI bundle; both Forge modules render it.

## Commands

Run from the app root (`app/`) unless noted.

```bash
pnpm install   # installs both workspace packages
pnpm ui:dev    # standalone Vite dev server (no Forge bridge)
pnpm build     # = pnpm ui:build → static/smiski-ui/dist
pnpm test      # = vitest run (smiski-ui)
pnpm lint      # biome check (whole app)
pnpm format    # biome format --write
pnpm typecheck # tsc --noEmit at root, then in smiski-ui

pnpm deploy       # = forge deploy (needs forge CLI + login)
pnpm install:site # = forge install
```

Single test / watch (from `static/smiski-ui`):

```bash
pnpm exec vitest run src/domain/meetingPolicy.test.ts
pnpm exec vitest # watch mode
```

`pnpm ui:dev` runs standalone: `App.tsx` detects `import.meta.env.DEV`, skips
the Forge `view.getContext()` call, and drives the surface via
`DevSurfaceSwitcher` instead.

## Architecture

**Dual-surface single bundle.** `manifest.yml` declares two modules that both
point at `resource: main` (`static/smiski-ui/dist`): `jira:issueContext` (key
`smiski-issue-context`) and `jira:projectPage` (key `smiski-project-page`).
`App.tsx` reads `context.moduleKey` and mounts `features/issue-panel/` or
`features/project-page/`; it also mounts modal roots from `features/shared/`
when `context.extension.modal.kind` is set. Module keys are centralized in
`utils/forgeModuleKeys.ts`. Put module-specific work under the matching
`features/` subtree, not in shared code.

**Resolver is a thin bridge, not a brain** (`src/index.ts`). Reality today:

- `searchWorkspaceUsers` — implemented; queries Jira as the invoking user via
  `@forge/api` `asUser().requestJira` through the `@smiskinext/sdks-jira` SDK
  (`src/jiraSdkClient.ts`), so Jira enforces the user's "Browse users"
  permission.
- `getRoomToken` — **prototype shim only.** Mints a LiveKit JWT locally with
  `livekit-server-sdk` from `LIVEKIT_API_KEY/SECRET/URL` Forge env vars. It does
  **no** authorization check — any user can mint a token for any `meetingId`.
  Replace with the backend `meet` token endpoint before production. Do not build
  on this behavior.
- `getIssueMeetings`, `scheduleMeeting`, `getProjectMeetings`,
  `getMeetingPermission` — stubs that throw. Do not add business logic here; the
  brain is the backend `meet` service.
- There is **no** `createInstantMeeting` resolver. Instant/scheduled creation
  goes straight from the Custom UI to the backend (see below).

**Backend calls go through Forge Remote + a generated SDK.**
`api/forgeRemoteFetch.ts` injects a `fetch`-shaped adapter into the
`@smiskinext/smiski-ts` SDK that routes every call through
`requestRemote('meet-backend', ...)`. Forge attaches a signed Forge Invocation
Token (FIT) as `Authorization: Bearer`; the app asserts no tenant/account
identity itself. The `meet-backend` remote and its `baseUrl`
(`${SMISKI_API_BASE_URL}`) are declared in `manifest.yml`.

**Frontend layering** (`static/smiski-ui/src/`):

- `domain/` — types, enums, and pure logic (e.g. `meetingPolicy.ts` with a
  colocated `.test.ts`). No side effects.
- `api/` — backend/Jira adapters. `config.ts` reads Vite env; `meetings.ts` (SDK
  instant/schedule + `getRoomToken`), `workspaceUsers.ts`/`getRoomToken`
  (resolver `invoke`), `currentUser.ts`/`projectMembers.ts`/`issues.ts` (Jira
  direct via `@forge/bridge` `requestJira`), `mappers.ts` (DTO↔domain).
- `hooks/` — TanStack Query hooks. `hooks/queryKeys.ts` centralizes every cache
  key; always add new keys there so mutation invalidation stays in sync.
- `context/`, `components/shared/`, `components/ui/` (local Tailwind design
  system), `features/`, `theme/`, `utils/`, `mocks/` (in-memory backend
  stand-in; `mocks/db.ts` is the store).

## Mock vs backend (gotcha)

Data source is **hardwired per hook**, not a global switch. The
`shouldUseBackendApi()` / `VITE_SMISKI_DATA_SOURCE` switch described in
`api/README.md` no longer exists — treat that README as aspirational.

- Reads (`useIssueMeetings`, `useProjectMeetings`, `useMeeting`, …) and
  `useUpdateMeeting`/`useCancelMeeting`/`useStartMeeting`/`useEndMeeting` →
  always `mocks/db.ts`.
- `useCreateInstantMeeting` / `useScheduleMeeting` → **real backend** via the
  SDK over Forge Remote, with **no mock fallback**. Standalone `vite dev`
  therefore cannot create meetings.
- `currentUser`/`projectMembers`/`issues` call Jira directly from the browser;
  `searchWorkspaceUsers`/`getRoomToken` go through the resolver.

## manifest.yml / egress

- `permissions.content.styles: [unsafe-inline]` is required — Ant Design injects
  CSS-in-JS at runtime, which Forge Custom UI blocks by default.
- `permissions.external.fetch` locks egress to `${SMISKI_API_BASE_URL}` and
  `${LIVEKIT_URL}`. Any new external origin must be added here or it is blocked
  at runtime.
- After changing scopes or egress you MUST `forge deploy` **and then**
  `forge install --upgrade` — a tunnel restart is not enough.
- Runtime is `nodejs24.x`, arm64, 256 MB. Env vars `SMISKI_API_BASE_URL` and
  `LIVEKIT_URL` have TODO placeholder defaults; `LIVEKIT_API_KEY/SECRET` are
  secrets set via `forge variables set` (needed for the `getRoomToken` shim).

## Conventions

- **Biome** is the only linter/formatter (`biome.json` extends root
  `../biome.json`). There is no ESLint or Prettier for app code. Style: 4-space
  indent, 80 columns, single quotes, semicolons, trailing commas everywhere,
  operator-linebreak before.
- The app's `biome.json` turns **off** `useImportExtensions`, so app imports
  omit the `.ts`/`.tsx` extension (`import x from './foo'`) — the opposite of
  the root default. Match surrounding files.
- Unused vars/imports are errors, but Biome ignores `_`-prefixed names — the
  resolver stubs use `_req` on purpose; don't "fix" those away.
- `@/*` path alias → `static/smiski-ui/src/*` (Vite + tsconfig). Code mixes it
  with relative imports; follow the local file.
- Env files: copy `.env.example` → `app/.env` for the Forge CLI/resolver, and
  `static/smiski-ui/.env.example` → `.env.local` for Vite.

## Forge CLI

- Every command except `create`/`version`/`login` must run from the app root
  (where `manifest.yml` lives).
- Use `--non-interactive` for `deploy`, `install`, `environments`; do not use it
  for other commands. Deploy to the development environment unless told
  otherwise; never `--no-verify` unless asked.
- Run `forge lint` after editing `manifest.yml`. Use `forge logs` (`-e <env>`,
  `--since 15m`) to debug a deployed app.
- Tunnel: redeploy + restart the tunnel after `manifest.yml` changes; code-only
  changes hot-reload without redeploy.

## Stale-doc warning

`api/README.md`, `PERMISSION.md`, and the `.vi.md` design notes describe the
intended end-state (a resolver `backendRequest` transport, a mock/backend
switch, an `api/` file list with `client.ts`/`endpoints.ts`/`participants.ts`/
`recordings.ts`) that the current code does not implement. When docs and code
disagree, trust the code.
