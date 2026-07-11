# app/AGENTS.md

Atlassian **Forge** app embedding meetings into a Jira issue panel. Read the
root `AGENTS.md` for repo-wide context first.

## Layout (this is Custom UI, NOT UI Kit)

- `src/index.ts` — Forge function backend: a `@forge/resolver` exposing resolver
  definitions (`handler = resolver.getDefinitions()`). Uses `@forge/api`
  (`api.asUser().requestJira(route\`...\`)`).
- `static/hello-world/` — Custom UI frontend: React 16 + `react-scripts` +
  `@forge/bridge`. This is a nested package with its own `package.json` and
  lockfile. `manifest.yml` serves its `build/` output as the `main` resource.
- `manifest.yml` — modules (`jira:issuePanel` → resolver `function`), resources,
  `permissions.scopes`, runtime (nodejs24.x, arm64). App id lives here.

Because the frontend is Custom UI (React DOM), the UI Kit component restrictions
do **not** apply — standard React + `@atlaskit` are used. Do not "fix" this to
UI Kit.

## Tooling

- Root `app/`: TypeScript resolver, linted by Biome (`pnpm --filter ... lint` or
  `biome check` here). `tsconfig.json` excludes `static/`.
- `static/hello-world/`: its own `npm`/`react-scripts` toolchain. Install and
  build there separately; `npm run build` emits `build/` consumed by the
  manifest.
- Dependencies are pinned to `-next` Forge prerelease versions — match existing
  versions when adding Forge packages.

## Forge CLI (run from `app/`, the Forge app root)

Every command except `create`/`version`/`login` must run in the Forge app root.
Verify cwd with `pwd`.

- `forge lint` — validate `manifest.yml` after any change; run before deploying.
- `forge deploy --non-interactive -e <env>` — default env is `development`.
- `forge install --non-interactive --site <url> --product <product> --environment <env>`;
  add `--upgrade` when scopes/permissions changed.
- `forge logs -e <env> --since 15m` — debug a deployed app.
- Use `--non-interactive` only for `deploy`, `environments`, `install`.

## Rules that bite

- Building `static/hello-world/build` is required before deploy — the manifest
  resource points at it. A stale/missing build ships old UI.
- Redeploy **and** reinstall after adding scopes or egress to `manifest.yml`.
- Tunnelling: redeploy + restart the tunnel on `manifest.yml` changes; code-only
  changes hot-reload without redeploy.
- Security: prefer `.asUser()` for product REST calls from resolvers (it does
  authz); if using `.asApp()` in a user context, do your own permission checks.
  Keep `permissions.scopes` minimal.
- Forge Storage/KVS/Custom Entities APIs are backend-only (`.asApp()` from
  resolvers) — no client-side API. Entity properties are managed via the Jira
  REST API, not a client SDK.
