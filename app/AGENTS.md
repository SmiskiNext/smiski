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

**Status:** meeting reads/mutations go straight from the Custom UI to the real
`meet` backend over Forge Remote + a generated SDK. Two resolver functions
(`getProjectMeetings`, `endMeeting`) still throw `Not implemented: ...`
because the backend has no matching operation yet — see "Resolver is a thin
bridge" below. Do not assume a code path is wired to the backend — check the
specific hook/resolver first.

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

pnpm run deploy       # = pnpm build && forge deploy
pnpm run install:site # = forge install
pnpm run forge <args> # = forge <args>, e.g. pnpm run forge logs --since 15m
```

Use `pnpm run deploy`, not `pnpm deploy` — `deploy` is a built-in pnpm 10
command and would shadow the script. The scripts call the Forge CLI directly,
so credentials and manifest interpolation variables must already be available
in the shell environment (or through Forge's normal login/configuration).
`deploy` rebuilds the UI first because `forge deploy` uploads
`static/smiski-ui/dist` as-is and never builds it.

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
point at `resource: main` (`static/smiski-ui/dist`): `jira:issuePanel` (key
`smiski-issue-panel`) and `jira:projectPage` (key `smiski-project-page`).
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
- `getMeetingPermission` — implemented; checks the invoking user's custom
  `View Meeting`/`Edit Meeting` Jira project permission (declared in
  `manifest.yml`'s `jira:projectPermission`, per `PERMISSION.md`) via
  `jiraSdkClient.ts`'s `getMeetingPermission` (`GET /rest/api/3/mypermissions`
  as the user, after resolving the real permission keys from
  `GET /rest/api/3/permissions` — Jira may not echo back the bare manifest
  `key`). **UI gating only** — the `meet` backend does not yet re-check this;
  see "Backend permission enforcement (not yet built)" below.
- `getProjectMeetings` — stub that throws. The real backend's `list`
  operation has no project-wide filter (only an exact `issueKey`, `creatorId`,
  `statuses`, or `search` filter), so the project-page dashboard listing has
  no backend to call yet.
- `endMeeting` — stub that throws. The backend has no explicit host-initiated
  "end meeting" operation yet; RUNNING→COMPLETED is expected to be driven by
  its LiveKit webhook handling instead of an explicit client call.
- There is **no** `getRoomToken`, `getIssueMeetings`, `scheduleMeeting`,
  `createInstantMeeting`, `getMeeting`, `updateMeeting`, `cancelMeeting`,
  `joinMeeting`, or `getHostConflict` resolver. All of those go straight from
  the Custom UI to the real `meet` backend via Forge Remote + the generated
  SDK (see below) — do not add resolver-side business logic for them; the
  brain is the backend `meet` service.

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
- `api/` — backend/Jira adapters. `config.ts` reads Vite env; `meetings.ts`
  (all real `meet` backend calls: instant/schedule/get/list/update/cancel/
  join, over the generated SDK + Forge Remote), `workspaceUsers.ts`
  (resolver `invoke`), `currentUser.ts`/`projectMembers.ts`/`issues.ts` (Jira
  direct via `@forge/bridge` `requestJira`), `mappers.ts` (DTO↔domain).
- `hooks/` — TanStack Query hooks. `hooks/queryKeys.ts` centralizes every cache
  key; always add new keys there so mutation invalidation stays in sync.
- `context/`, `components/shared/`, `components/ui/` (local Tailwind design
  system), `features/`, `theme/`, `utils/`, `mocks/` (Jira identity/issue
  fixtures for `vite dev` only — see "Mock vs backend" below).

## Mock vs backend (gotcha)

`mocks/db.ts` is **gone** — meeting persistence is never mocked. Most meeting
hooks call the real `meet` backend directly via the generated SDK over Forge
Remote (`api/meetings.ts`): `useMeeting`, `useMeetingParticipants` (derived
from the same `getMeeting` query), `useIssueMeetings`, `useCreateInstantMeeting`,
`useScheduleMeeting`, `useUpdateMeeting`, `useCancelMeeting`, `useStartMeeting`
(joins as host), and `useRoomToken`. Two exceptions still go through resolver
stubs that throw (`getProjectMeetings`, `endMeeting` — see "Resolver is a thin
bridge" above): `useProjectMeetings` and `useEndMeeting`. Standalone
`vite dev` therefore cannot list/create/update/cancel/start meetings or enter
a room — there is no Forge bridge and no Forge Remote binding to reach the
backend through.

The `shouldUseBackendApi()` / `VITE_SMISKI_DATA_SOURCE` switch described in
`api/README.md` never existed in code — treat that README as aspirational.

What remains in `mocks/` is Jira **identity/issue** data only, and every one of
its consumers is gated on `import.meta.env.DEV`, so a Forge build never reaches
it:

- `useProjectIssues`, `useProjectMembers` → real Jira via `requestJira` from the
  browser; mock only under `vite dev`.
- `useWorkspaceUsers` → resolver `searchWorkspaceUsers`; mock only under
  `vite dev`.
- `useMeetingPermission` → resolver `getMeetingPermission`; mocked (always full
  access) only under `vite dev`.
- `CurrentUserContext` → Jira `/myself`; `mocks/users.ts`'s `CURRENT_USER` is
  the context default and the `vite dev` value. `CurrentUserProvider` wraps the
  whole app including modal roots (`App.tsx`), so the default never leaks into
  a Forge render.

## Backend permission enforcement (not yet built)

`meet` has **no** Jira-permission concept today — only a host-ownership check
on `update`/`delete` (`hostId.equals(...)`), nothing on `list`/`get`/`join`.
Until backend enforcement exists, `View Meeting`/`Edit Meeting` is **UI-only**
— exactly the anti-pattern `PERMISSION.md` §7 warns against (frontend hiding
is not a security layer). Researched mechanism for the follow-up, so it
doesn't need re-discovering:

- Forge Remote backends **can** call Jira REST APIs directly: enable
  `appUserToken` (needs the `read:app-user-token` scope) on the relevant
  `endpoint` entries in `manifest.yml` (currently only `meet-endpoint` /
  `/api/1/meetings:instant` exists, and has both `appUserToken`/
  `appSystemToken` disabled — each distinct backend path needs its own
  `endpoint` entry with matching `route.path`, since auth attaches per
  declared endpoint, not globally per remote).
- The token arrives at `meet` in the `x-forge-oauth-user` header; use it as a
  `Bearer` token against the FIT's `apiBaseUrl` claim (**not** the site URL)
  to call `GET /rest/api/3/mypermissions` directly from Java
  (`RestTemplate`/`WebClient`) — mirrors the same `getMeetingPermission`
  logic already implemented in `jiraSdkClient.ts`, just from the backend
  instead of the resolver.

## manifest.yml / egress

- `permissions.content.styles: [unsafe-inline]` is required — Ant Design injects
  CSS-in-JS at runtime, which Forge Custom UI blocks by default.
- `permissions.external.fetch` locks egress to `${SMISKI_API_BASE_URL}` and
  `${LIVEKIT_URL}`. Any new external origin must be added here or it is blocked
  at runtime.
- After changing scopes or egress you MUST `forge deploy` **and then**
  `forge install --upgrade` — a tunnel restart is not enough.
- Runtime is `nodejs24.x`, arm64, 256 MB. Env vars `SMISKI_API_BASE_URL` and
  `LIVEKIT_URL` have TODO placeholder defaults. There is no
  `LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET` in this app — room tokens come from
  the backend `meet` service's `join` endpoint, so only the backend needs the
  LiveKit secret.

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

`api/README.md` and the `.vi.md` design notes describe an intended end-state
(a resolver `backendRequest` transport, a mock/backend switch, an `api/` file
list with `client.ts`/`endpoints.ts`/`participants.ts`/`recordings.ts`) that
the current code does not implement. When docs and code disagree, trust the
code. `PERMISSION.md`'s permission *model* (View/Edit Meeting, the
state×permission matrix) is accurate and now partially implemented (frontend
+ resolver, per "Backend permission enforcement" above) — its §7 backend
re-check requirement is the part still outstanding.
