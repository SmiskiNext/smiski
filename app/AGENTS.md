# AGENTS.md — Smiski Forge app

Rules for `app/`. Root repo rules: `../AGENTS.md`.

## What this is

Atlassian **Forge Custom UI** app embedding the Smiski meeting module into Jira.
**Custom UI, not UI Kit** — ignore any advice to use `@forge/react` /
`@forge/ui`. Plain React 18 + Tailwind v4 + Ant Design. **There is no Forge
resolver/function here**: backend calls go over Forge Remote, Jira calls go
straight from the browser via `@forge/bridge` `requestJira` (bridge v2+), and
nothing reads `process.env`.

## Layout

`app/` is its **own pnpm workspace root** (`app/pnpm-workspace.yaml`,
`app/pnpm-lock.yaml`), separate from the repo lockfile. Members: `.`
(`manifest.yml` + Forge CLI, no code), `static/smiski-ui/` (the single Custom UI
bundle both modules render — Vite + React + TS + TanStack Query + AntD + LiveKit
client), and `../sdks/typescript` linked as `workspace:*`.

## Setup gotchas (hit these first)

- `@smiskinext/sdks-jira` is **private on GitHub Packages**. `pnpm install`
  fails without `~/.npmrc` auth for the `@smiskinext` scope (CI does this in
  `.github/actions/configure-github-packages`).
- `@smiskinext/smiski-ts` ships from `dist/`, which is **gitignored**. Build it
  before typecheck/build: `pnpm --filter @smiskinext/smiski-ts build`.
- Root `lefthook.yml`'s app hooks are **stale**: `typecheck-app` runs
  `pnpm exec tsc --noEmit` in `app/` where there is no `tsconfig.json` and no
  `tsc` binary; `format-app` globs `app/src/**` (doesn't exist) and calls
  eslint/prettier, which this app doesn't use. Use the scripts below instead.

## Commands (from `app/`)

```bash
pnpm install
pnpm ui:dev    # standalone Vite, no Forge bridge
pnpm build     # tsc --noEmit && vite build -> static/smiski-ui/dist
pnpm test      # vitest run
pnpm lint      # biome check
pnpm typecheck # smiski-ui only
pnpm run deploy       # build + forge deploy
pnpm run forge <args> # e.g. pnpm run forge logs --since 15m
```

`pnpm run deploy`, never `pnpm deploy` — pnpm 10 has a built-in `deploy` that
shadows the script. `forge deploy` uploads `dist/` as-is and never builds it.
Single test, from `static/smiski-ui`:
`pnpm exec vitest run src/domain/meetingPolicy.test.ts`. There is no
`vitest.config.ts` — DOM tests opt in per file with a
`// @vitest-environment jsdom` first-line pragma. Add it to any new `.tsx` test.

## Architecture

**Dual-surface, one bundle.** `manifest.yml` points `jira:issuePanel`
(`smiski-issue-panel`) and `jira:projectPage` (`smiski-project-page`) at the
same `resource: main`. `App.tsx` branches on `context.moduleKey`
(`utils/forgeModuleKeys.ts`) to mount `features/issue-panel/`,
`features/project-page/`, or a `features/shared/` modal root when
`context.extension.modal.kind` is set. Keep surface work in its own subtree.

**Two separate iframes, no shared React tree.** Issue Panel → Project Page room
navigation hands off through `localStorage` (`utils/meetingRoomHandoff.ts`).
Forms too wide for the panel iframe open as Forge platform modals
(`@forge/bridge` `Modal`) via `hooks/useIssuePanel*Modal.ts`, which fall back to
in-page modals under `vite dev`.

**Transports** (`static/smiski-ui/src/api/`):

- `forgeRemoteFetch.ts` — `fetch`-shaped adapter injected into
  `@smiskinext/smiski-ts` so backend calls go through `invokeRemote`, resolved
  via the module's `resolver.endpoint`; the Forge platform proxy attaches the
  FIT **and** the app system token (`x-forge-oauth-system`), which
  `requestRemote` cannot. The adapter injects `x-issue-id` / `x-project-id` from
  `api/backendContext.ts` and asserts no tenant/account identity itself.
- `jiraSdkFetch.ts` — same trick for `@smiskinext/sdks-jira` over `requestJira`
  (`workspaceUsers.ts`, `meetingPermission.ts`). `currentUser.ts` / `issues.ts`
  / `projectMembers.ts` call `requestJira` raw.
- `meetingEvents.ts` + `sseClient.ts` — the SSE join-request stream uses a **raw
  `fetch`** to `apiConfig.apiBaseUrl`, bypassing Forge Remote. Same for LiveKit
  `room.connect`. Both origins need `external.fetch.client` entries.

**Layering:** `domain/` pure logic + colocated tests · `api/` adapters ·
`hooks/` TanStack Query — register every cache key in `hooks/queryKeys.ts` so
mutation invalidation stays in sync · `components/ui/` local Tailwind
primitives.

## Wiring status

Project meeting listing uses the backend's cursor-paginated `list` operation
with an exact `projectKey` filter. Issue Panel listing uses the dedicated,
offset-paginated `/issues/{issueId}/meetings` operation. Correctness-sensitive
conflict checks walk every cursor page. Everything else
(instant/schedule/get/update/cancel/end/settings/join/token) is real. Check the
specific hook before assuming either direction. Meeting persistence is **never**
mocked and there is no mock/backend switch: `mocks/` is Jira identity/issue data
only, every consumer gated on `import.meta.env.DEV`, so standalone `vite dev`
cannot create/list/start meetings or enter a room.

## Permissions

`getMeetingPermission` checks the custom `View Meeting`/`Edit Meeting` project
permissions (declared as `jira:projectPermission`) via
`GET /rest/api/3/mypermissions`, resolving real permission keys from
`GET /rest/api/3/permissions` first — Jira does not echo back the bare manifest
key, so match on `name`.

## manifest.yml

- `content.styles: [unsafe-inline]` is required — AntD injects CSS-in-JS.
- After changing scopes or egress: `pnpm exec forge deploy` **then**
  `pnpm exec forge install --upgrade` — a tunnel restart is not enough. Run
  `pnpm exec forge lint` after editing. Forge commands need `app/` as cwd.

## Deploy

`.github/workflows/forge-deploy.yml`: `development` auto on push to `dev`,
`staging` manual only. Forge `production` is intentionally unused (it forbids
`forge tunnel`/`forge logs`). `main` does not deploy. Values come from GitHub
Environment `vars`/`secrets`, validated before build.

## Conventions

- **Biome only** — no ESLint/Prettier here. 4-space, 80 cols, single quotes,
  trailing commas, operator-linebreak before. App `biome.json` turns
  `useImportExtensions` **off** — omit `.ts`/`.tsx` in imports, the opposite of
  the root default.
- Unused vars are errors, but `_`-prefixed names are exempt (e.g.
  `listProjectMeetings`'s `_filters`) — don't "fix" them.
- `@/*` → `static/smiski-ui/src/*`; code mixes it with relative imports.
- `README.md` still calls backend calls stubbed — outdated, trust the code.
