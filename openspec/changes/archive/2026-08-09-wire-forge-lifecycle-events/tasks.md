## 1. App dependencies and function scaffolding

- [x] 1.1 Add `@forge/api` and `@smiskinext/smiski-ts` (`workspace:*`) to
      `app/package.json` `dependencies`; keep `@forge/cli` pinned at `13.2.0`
- [x] 1.2 Create the Forge function source root (Forge default `src/`) at the
      app root alongside `manifest.yml`, with a TypeScript entry that Forge's
      bundler compiles on `deploy`/`tunnel` (do not route it through the Vite UI
      build) ← (verify: `pnpm exec forge lint` passes; UI Vite build output
      under `static/smiski-ui/dist` is unchanged)

## 2. Server-side SDK transport adapter

- [x] 2.1 Add a `fetch`-shaped transport adapter (server-side analog of
      `static/smiski-ui/src/api/forgeRemoteFetch.ts`) that calls `@forge/api`
      `invokeRemote('meet-backend', { path, method, headers, body })`,
      forwarding the SDK-built `pathname + search`, method, and JSON body, and
      reconstructs a WHATWG `Response` from the `{ ok, status, headers, body }`
      result
- [x] 2.2 Build a dedicated `@smiskinext/smiski-ts` client via
      `createClient(createConfig({ baseUrl: <placeholder>, fetch }))` using the
      adapter; ensure it injects no `X-Issue-Id`/`X-Project-Id` and no
      tenant/account identity headers ← (verify: adapter sends only the
      SDK-composed headers plus Forge-attached FIT; no context or identity
      headers added, matching design D2)

## 3. Lifecycle handler functions

- [x] 3.1 Implement the install/upgrade handler: map the
      `avi:forge:installed:app` / `avi:forge:upgraded:app` event payload to the
      SDK `TenantRegisterTenantRequest` (`id`, `installerAccountId?`, `app`,
      `environment?`), call `register({ client, path: { version }, body })`, and
      throw on a result whose `error` is present so Forge retries
- [x] 3.2 Implement the pre-uninstall handler: call
      `uninstall({ client, path: { version } })`, treat both `200` and `404`
      results as terminal success, and never throw ← (verify: install/upgrade
      throws on error to trigger Forge retry; pre-uninstall treats 200 and 404
      as success and does not throw, matching design D3/D4)

## 4. Manifest module wiring

- [x] 4.1 Add a `trigger` module subscribing to `avi:forge:installed:app` and
      `avi:forge:upgraded:app` bound to the install/upgrade `function`
- [x] 4.2 Add a `preUninstall` module bound to the uninstall `function`, and the
      two `function` entries referencing the handlers from group 3
- [x] 4.3 Confirm the existing `meet-backend` remote declares
      `operations:     [compute]` (prerequisite for function-side
      `invokeRemote`); add no new scopes or egress ← (verify:
      `pnpm exec forge lint` clean; no scope/egress diff versus current
      manifest; `meet-backend` reused, not duplicated)

## 5. Gateway — principal becomes optional

- [x] 5.1 In `services/gateway/internal/fit/parser.go`, stop returning
      `ErrMissingClaims` when `principal` is empty; derive `accountId` from
      `principal` only when present, otherwise leave it empty
- [x] 5.2 Keep cloudId resolution mandatory (`context.cloudId` →
      `app.apiBaseUrl` fallback) and continue to return a missing-claims error
      only when no cloudId can be resolved ← (verify: a FIT with a resolvable
      cloudId and no principal parses successfully with empty accountId; a FIT
      with no cloudId is still rejected; deny message never cites a missing
      principal, matching design D5)

## 6. App-side tests (from spec scenarios)

- [x] 6.1 install-app: install event calls `register()` over the Forge Remote
      SDK transport and reads the tenant snapshot from result `data`
      (install-app: Install event records the tenant through the SDK over Forge
      Remote)
- [x] 6.2 install-app: upgrade event calls the same `register()` with the
      upgrade payload and treats `200` as success (install-app: Major upgrade
      event also calls register)
- [x] 6.3 install-app: register request asserts no identity and no context
      headers (install-app: Forge attaches the FIT and the function asserts no
      identity or context)
- [x] 6.4 install-app: a result with `error` present makes the handler throw for
      Forge retry (install-app: Non-success response triggers Forge retry)
- [x] 6.5 uninstall-app: pre-uninstall calls `uninstall()` (`DELETE`) over the
      Forge Remote SDK transport and treats `200` as success (uninstall-app:
      Pre-uninstall event records the uninstall through the SDK over Forge
      Remote)
- [x] 6.6 uninstall-app: uninstall request asserts no identity and no context
      headers (uninstall-app: Forge attaches the FIT and the function asserts no
      identity or context)
- [x] 6.7 uninstall-app: a `404` result is treated as success without throwing
      (uninstall-app: Unknown tenant is treated as success, not failure)
- [x] 6.8 uninstall-app: a `200` for an already-uninstalled tenant is treated as
      success without throwing (uninstall-app: Already-uninstalled tenant is
      treated as success) ← (verify: every app-side scenario above has a passing
      test; `pnpm --filter smiski-ui test` or the function test runner green)

## 7. Gateway tests (from spec scenarios)

- [x] 7.1 permission-checking: absent `principal` with a resolvable cloudId
      parses successfully with empty accountId (permission-checking: Missing
      principal yields an empty accountId, not a rejection)
- [x] 7.2 permission-checking: empty `principal` with a resolvable cloudId
      parses successfully with empty accountId (permission-checking: Empty
      principal yields an empty accountId, not a rejection)
- [x] 7.3 permission-checking: neither cloudId source present is still rejected,
      and the message does not cite a missing principal (permission-checking:
      Missing cloudId claims reported)
- [x] 7.4 permission-checking: existing colon-prefixed / ARI / bare principal
      extraction still passes unchanged (permission-checking: AccountId
      extracted from Forge colon-prefixed / ARI / bare principal)
- [x] 7.5 permission-checking: a valid cloudId with absent accountId yields an
      allow decision with `X-Tenant-ID` set and empty `X-Account-Id`, and, with
      no context headers, empty permissions (permission-checking: Allow decision
      with empty accountId when principal is absent; Valid cloudId with absent
      accountId is not denied) ← (verify: `parser_test.go` and authz tests cover
      absent/empty principal allow-path and missing-cloudId deny-path; existing
      principal cases remain green)

## 8. Verification

- [x] 8.1 `pnpm exec forge lint` in `app/`, then `pnpm --filter smiski-ui test`
      and the function tests pass; `pnpm lint` (biome) clean
- [x] 8.2 `./services/gradlew -p services/gateway test` (or the gateway Go test
      command) passes with the optional-principal parser changes
- [x] 8.3 Confirm deploy sequence is documented for the operator: `forge deploy`
      **then** `forge install --upgrade` (module change; no new scope/egress) ←
      (verify: change is deployable end to end — lint clean, app + gateway tests
      green, no `tenant`/SDK/Envoy-route edits introduced)
