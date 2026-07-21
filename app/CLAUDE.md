# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

This directory (`Smiski_app`) is the **Atlassian Forge app only** — the Jira-embedded
client for the Smiski online-meeting module. It is a **client-only** Forge app: it
renders UI inside Jira and talks to the backend exclusively through a Kong Gateway
(which carries the invoking Jira user's identity). It never talks to Postgres, Kafka,
or LiveKit-server directly — the sole exception is the LiveKit client SDK used inside
the meeting room for WebRTC media.

The backend services (`tenant`, `meet`, `record`, `notification` — Spring Boot,
hexagonal architecture) and the wider system design live outside this directory; see
`architecture.vi.md` (Vietnamese, with embedded D2 diagrams) for the full system
architecture, event topics, CQRS/replica routing, Valkey key map, and known gaps
between the design and current backend code. `DOCS1.md` describes the overall Smiski
project (this Forge app is one piece of it).

**Status:** structural scaffold. The resolver (`src/index.ts`) and API client
(`static/smiski-ui/src/api/`) are stubs that `throw new Error('Not implemented: ...')`
— no business logic, auth bridging, or backend wiring exists yet. The frontend runs
against local mocks (`static/smiski-ui/src/mocks/`) instead. Files needing logic are
marked `TODO`.

## Repo layout

Two pnpm workspace packages:

- **root** (`package.json`) — the Forge app itself: `manifest.yml` + `src/index.ts`
  (resolver). Plain TypeScript, no framework.
- **`static/smiski-ui/`** — the Custom UI frontend: Vite + React 18 + TypeScript +
  Tailwind v4 + TanStack Query. This is the *only* UI bundle; both Forge modules
  (`jira:issueContext` and `jira:projectPage`, declared in `manifest.yml`) render it
  and branch at runtime on `context.moduleKey` (see `App.tsx`).

## Commands

Run from the repo root unless noted.

```bash
pnpm install            # installs both workspace packages
pnpm ui:dev             # vite dev server for the Custom UI, standalone (no Forge bridge)
pnpm build              # = pnpm ui:build -> static/smiski-ui/dist
pnpm test               # = pnpm --filter smiski-ui test (vitest run)
pnpm lint               # eslint on root src/**/*.ts, then eslint in smiski-ui
pnpm typecheck          # tsc --noEmit at root, then in smiski-ui
pnpm format / format:check   # prettier, whole repo

forge deploy            # deploy the Forge app (requires forge CLI + login)
forge install           # install on a site
```

Single test file / watch mode (inside `static/smiski-ui`):

```bash
pnpm --filter smiski-ui exec vitest run src/domain/meetingPolicy.test.ts
pnpm --filter smiski-ui exec vitest        # watch mode
```

Local frontend dev with no real Jira/Forge context: `pnpm ui:dev` runs standalone
Vite. `App.tsx` detects `import.meta.env.DEV` and skips the Forge `view.getContext()`
call, driving the surface (`issueContext` vs `projectPage`) instead via
`DevSurfaceSwitcher` (see `components/DevSurfaceSwitcher.tsx`).

## Architecture (this app)

**Dual-surface single bundle.** `manifest.yml` declares two Forge modules that both
point at the same `resource: main` (`static/smiski-ui/dist`):
`jira:issueContext` (per-Issue panel, module key `smiski-issue-context`) and
`jira:projectPage` (project-wide dashboard + meeting room, module key
`smiski-project-page`). `App.tsx` reads `context.moduleKey` and mounts either
`features/issue-panel/IssuePanelRoot` or `features/project-page/ProjectPageRoot`.
When adding a module-specific feature, put it under the matching `features/`
subtree, not in shared code, unless it's genuinely used by both surfaces.

**Resolver holds no business logic.** `src/index.ts` defines named resolver functions
(`getIssueMeetings`, `createInstantMeeting`, `scheduleMeeting`, `getProjectMeetings`,
`getMeetingPermission`, `getRoomToken`) that are meant to stay a thin bridge: read the
invoking user's context, forward to the backend (`tenant`/`meet`/`record` services)
via Kong with the user's identity attached, return the result. The "business brain"
lives in the backend `meeting-management`/`meet` service, not here.

**Frontend layering** (`static/smiski-ui/src/`):

- `domain/` — types + enums mirroring the backend domain model, plus pure logic
  (e.g. `meetingPolicy.ts` — has a colocated `.test.ts`). No side effects.
- `api/` — typed Kong Gateway client. `client.ts` is the shared `apiRequest<T>()`
  wrapper (currently a stub); `meetings.ts`/`participants.ts`/`recordings.ts` are
  resource-specific typed calls built on top of it.
- `hooks/` — TanStack Query hooks per resource (`useIssueMeetings`, `useMeeting`,
  `useMeetingMutations`, etc.). `hooks/queryKeys.ts` centralizes all query-key
  factories — always add new cache keys there so invalidation in mutation hooks
  stays in sync with the query hooks reading the same data.
- `context/` — React context for cross-cutting app state (`CurrentIssueContext`,
  `CurrentUserContext`), not per-feature state.
- `components/shared/` — reusable, surface-agnostic building blocks (cards, modals,
  status tags, action-menu rule logic in `meetingActionRules.ts`).
- `components/ui/` — low-level presentational primitives (Button, Modal, Avatar,
  SelectDropdown) — the local design-system layer, styled with Tailwind.
- `features/issue-panel/` and `features/project-page/{dashboard,meeting-room}/` —
  surface-specific screens/composition, consuming `hooks/` + `components/`.
- `mocks/` — the frontend-only stand-in for the Kong Gateway backend while
  `api/`/resolvers are unimplemented; `mocks/db.ts` is the in-memory store,
  `meetings.ts`/`participants.ts`/`recordings.ts`/`users.ts` seed it.

**Egress is locked down.** `manifest.yml`'s `permissions.external.fetch` lists the
exact origins the app may call (`backend` for resolver-side fetch, `client` for
Custom UI fetch/WebSocket — needed for the LiveKit client SDK). These are currently
`TODO` placeholders; any new external call must be added to this allowlist or it will
be blocked at runtime, and per Forge rules, changing scopes/egress requires
`forge deploy` + `forge install --upgrade` (not just a tunnel restart).

## Conventions

- Prettier: single quotes, semicolons, trailing commas everywhere, 100-col width,
  2-space indent (`.prettierrc.json`).
- Root ESLint config (`.eslintrc.cjs`) intentionally allows unused underscore-prefixed
  args and empty functions — this repo has many intentional stub signatures; don't
  "fix" those away unless you're implementing the stub.
- `static/smiski-ui` has its own separate ESLint config; don't assume the root config
  applies to it.
