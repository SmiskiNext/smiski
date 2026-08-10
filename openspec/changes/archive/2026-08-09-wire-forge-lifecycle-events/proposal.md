## Why

The `tenant` backend already exposes `POST /tenants` (install/upgrade) and
`DELETE /tenants` (uninstall), and `@smiskinext/smiski-ts` already ships
`register()` and `uninstall()` for them — but nothing in the Forge app ever
calls them. The app-side wiring was explicitly deferred when `install-app` and
`uninstall-app` were built. Until it exists, installing the app on a Jira site
records no tenant, so `meet`/`notification` never receive the tenant projection
they depend on, and uninstalling never starts the data-purge window. This change
delivers the missing half: the Forge lifecycle events that drive those endpoints
end to end.

## What Changes

- **BREAKING (manifest):** Add a Forge `trigger` module subscribing to
  `avi:forge:installed:app` and `avi:forge:upgraded:app`, a `preUninstall`
  module, and the `function` module(s) they invoke. Editing modules/endpoints
  requires `forge deploy` **then** `forge install --upgrade` with administrator
  re-consent. `avi:forge:upgraded:app` fires only on **major** version upgrades.
- The install/upgrade function calls the SDK `register()`; the pre-uninstall
  function calls the SDK `uninstall()` (HTTP `DELETE`). Both invoke the `meet`
  backend through **`@forge/api` `invokeRemote`** (server-side), so Atlassian
  attaches the signed Forge Invocation Token (FIT). This is a new, server-side
  transport distinct from the browser `@forge/bridge` transport in
  `forgeRemoteFetch.ts`; the app is no longer function-free.
- The lifecycle functions send **no** `x-issue-id`/`x-project-id` context
  headers (there is no issue/project scope), and assert no tenant/account
  identity — tenant identity is derived by the gateway from the FIT.
- Install/upgrade delivery throws on a non-success response so Forge's built-in
  event retry (up to 4 attempts) applies. Pre-uninstall delivery is single-shot
  (55s, non-blocking, no retry) and treats both `200` (marked uninstalled or
  already-uninstalled) and `404` (unknown tenant) as terminal success.
- **BREAKING (gateway authorization):** Make the FIT `principal` claim
  **optional** in the authorization service. Lifecycle invocations run without a
  user, so the FIT carries no `principal`; today the parser rejects that with
  `403`, blocking every lifecycle call. Tenant identity (cloudId) continues to
  come from `context.cloudId`/`app.apiBaseUrl`; `accountId` is empty when
  `principal` is absent. Requests still lacking a valid cloudId are denied.

## Capabilities

### New Capabilities

<!-- None. This change extends existing capabilities only, per the agreed scope. -->

### Modified Capabilities

- `install-app`: Add the app-side delivery requirement — the Forge app SHALL
  forward `avi:forge:installed:app` and `avi:forge:upgraded:app` to the tenant
  install endpoint through a Forge trigger and a function that calls the SDK
  `register()` over Forge Remote, failing (to trigger Forge retry) on a
  non-success response.
- `uninstall-app`: Add the app-side delivery requirement — the Forge app SHALL
  forward the `preUninstall` lifecycle event to the tenant uninstall endpoint
  through a `preUninstall` function that calls the SDK `uninstall()` (`DELETE`)
  over Forge Remote, treating `200` and `404` as success within the 55s window.
- `permission-checking`: Make the FIT `principal` claim optional so user-less
  lifecycle (and other app-level) invocations parse successfully and resolve
  tenant identity from the cloudId; `accountId` is empty when `principal` is
  absent, and denial is driven by a missing cloudId rather than a missing
  principal.

## Impact

- **App manifest** (`app/manifest.yml`): new `trigger`, `preUninstall`, and
  `function` modules; confirm the `meet-backend` remote exposes `compute` for
  function-side `invokeRemote` (it already lists `compute`). Breaking install.
- **App code** (`app/static/smiski-ui/` or a new function entry): lifecycle
  handler(s) mapping install/upgrade → `register()` and preUninstall →
  `uninstall()`, plus a `@forge/api`-based SDK transport adapter (server-side
  analog of `forgeRemoteFetch.ts`, injecting no context/identity headers).
- **App dependencies** (`app/package.json`): add `@forge/api`; add the build
  step that bundles the Forge function (today only the Vite UI is built).
- **Gateway** (`services/gateway/internal/fit/parser.go` + `parser_test.go`):
  `principal` becomes optional; `accountId` empty when absent; deny only on
  missing cloudId. No change to `authz/service.go`'s existing no-context-headers
  skip path, which lifecycle calls rely on.
- **No change** to the `tenant` service, the generated SDK, Kafka consumers, or
  the Envoy `/api/1/tenants` route — all already in place.
- **Tests**: app-side handler/transport unit tests; gateway FIT parser tests for
  the optional-principal cases.
