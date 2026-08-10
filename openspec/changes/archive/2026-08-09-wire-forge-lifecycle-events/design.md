## Context

The `tenant` backend is complete on the receiving side: `POST /api/1/tenants`
(`register`, idempotent upsert → `201`/`200`) and `DELETE /api/1/tenants`
(`uninstall`, → `200`, `404` when unknown) are live, their request DTOs already
match the Forge lifecycle payload shapes, and `@smiskinext/smiski-ts` already
generates `register()` and `uninstall()` for them. `meet` and `notification`
consume the resulting `TenantInstalled`/`TenantUninstalled` Kafka events. The
`install-app` and `uninstall-app` changes deliberately deferred "the Forge app
wiring that forwards the event" (see their archived proposals' Non-Goals).

Nothing in `app/` calls those endpoints. Per `app/AGENTS.md` the app is
intentionally **function-free**: both UI modules render one Vite bundle and all
backend traffic goes through `@forge/bridge` `invokeRemote` from inside the
iframe (`api/forgeRemoteFetch.ts`). Lifecycle events have no iframe and no
browser, so `@forge/bridge` cannot serve them.

Two platform facts (from the Forge docs) shape the design:

- The `trigger` and `preUninstall` modules each accept **either** a `function`
  **or** an `endpoint` (Forge Remote). An `endpoint` auto-route is **POST-only**
  and forwards the raw event payload — it never invokes the SDK. The requirement
  is to reuse the SDK `register()`/`uninstall()` (the latter is `DELETE`), so a
  **function** is required; the app can no longer be function-free.
- A Forge function calls a remote with `@forge/api`
  `invokeRemote(remoteKey, {path, method, headers, body})`. Forge attaches the
  signed FIT automatically. For backend (function-to-remote) invocations, OAuth
  tokens are governed by `remotes.auth`, not `endpoint.auth`.

The blocking constraint is in the gateway. Envoy's `jwt_authn` verifies the FIT
signature and the Lua filter derives `x-fit-cloud-id` from `app.apiBaseUrl`
(tolerating an absent `principal`). But the Go authorization service's
`fit.Parse` (`services/gateway/internal/fit/parser.go:89-91`) rejects a FIT with
no `principal` as `ErrMissingClaims`, which `authz/server.go:72-73` turns into a
`403`. The Forge Remote docs state `principal` is **"UI modules only"** — a
lifecycle invocation runs without a user and carries none, so every lifecycle
call would be denied before reaching the tenant service.

Constraints from `openspec/specs/`:

- **api-convention**: `/api/{version}` versioned routes; success bodies carry no
  envelope; errors are Problem Details served as `application/json` so a Forge
  Remote client can read `code`/`traceId`. The tenant endpoints already comply;
  the app-side handlers only consume these responses.
- **permission-checking**: tenant identity (cloudId) is derived by the gateway
  from the FIT, never asserted by the caller; a request with neither
  `x-issue-id` nor `x-project-id` skips the Jira permission check and returns an
  empty permission set (`authz/service.go:88-91`). Lifecycle calls rely on this
  skip path unchanged.

## Goals / Non-Goals

**Goals:**

- Forward `avi:forge:installed:app` and `avi:forge:upgraded:app` to
  `register()`, and `preUninstall` to `uninstall()`, over Forge Remote so the
  FIT is attached and the gateway resolves the tenant.
- Reuse the generated SDK operations rather than hand-rolling HTTP.
- Make the gateway accept a user-less FIT so lifecycle (and any future
  app-level) invocation authenticates on cloudId alone.
- Match Forge's delivery semantics: retry-friendly install/upgrade, single-shot
  idempotent pre-uninstall.

**Non-Goals:**

- Any change to the `tenant` service, the generated SDK, the Kafka consumers, or
  the Envoy `/api/1/tenants` route — all already in place.
- Minor/patch upgrade handling (`avi:forge:upgraded:app` fires on **major**
  upgrades only; this is a platform behavior, not our choice).
- Reusing the browser transport `forgeRemoteFetch.ts` for lifecycle, or moving
  the SSE/LiveKit egress.
- Granting the gateway the ability to authorize meeting actions without a user —
  a user-less FIT yields an empty `accountId`, so permission-scoped routes
  naturally resolve zero permissions and remain denied.

## Decisions

### D1: A Forge function invokes the SDK; the app stops being function-free

`trigger` (install + upgrade) and `preUninstall` modules reference `function`
modules, not `endpoint`s. The handlers call the SDK's
`register()`/`uninstall()`. Rationale: the `endpoint` auto-route is POST-only
and bypasses the SDK, so it cannot express a `DELETE` uninstall or reuse the
generated client. Alternative (function-free `endpoint` route) rejected: it
contradicts the explicit "use the SDK" requirement and would need a second,
divergent request contract.

This is a deliberate, documented departure from `app/AGENTS.md`'s function-free
stance; the design note there covers UI traffic only.

### D2: Server-side SDK transport over `@forge/api` `invokeRemote`

Add a transport adapter that is the server-side analog of
`api/forgeRemoteFetch.ts`: a `fetch`-shaped function injected into a dedicated
`@smiskinext/smiski-ts` client via
`createClient(createConfig({ baseUrl: <placeholder>, fetch }))`. Instead of
`@forge/bridge`, it calls `@forge/api`
`invokeRemote('meet-backend', { path, method, headers, body })`, forwarding the
SDK-built `pathname + search`, method, and JSON body, and reconstructs a WHATWG
`Response` from the `{ ok, status, headers, body }` result so the SDK's zod
validation and result mapping keep working.

The adapter injects **no** `x-issue-id`/`x-project-id` headers (lifecycle has no
issue/project scope; sending them would push the gateway onto the Jira
permission-check path, which needs a system token and a real account) and **no**
tenant/account identity headers (the gateway derives those from the FIT).
Rationale: mirrors the existing transport's contract while matching lifecycle's
context-free nature. Alternative (call `invokeRemote` directly, skip the SDK)
rejected: the requirement is to use the SDK, and the SDK owns path building and
response validation.

The remote is addressed by key (`meet-backend`), which already declares
`operations: [compute]` — the prerequisite for function-side `invokeRemote`. No
`endpoint`/`resolver.endpoint` is involved on this path, and no OAuth token is
needed (the tenant endpoints call neither Jira nor a permission check), so
`remotes.auth` need not change.

### D3: Both install and upgrade map to `register()`

`avi:forge:installed:app` and `avi:forge:upgraded:app` carry the same core
payload (`id`, `installerAccountId?`, `app{ id, version, … }`,
`environment{ id }?`; upgrade adds `permissions`). Both map directly to the SDK
`TenantRegisterTenantRequest` and call `register()`. The backend upsert is
idempotent and reactivating (per `install-app`: "Reinstall updates installation
id and reactivates"), so an upgrade is just another `register()` that refreshes
`installation_id`/version. Rationale: one code path, and the backend already
defines upsert as the correct behavior for redelivery and reinstall.
`permissions` on the upgrade payload is ignored (the backend contract has no
field for it). Alternative (a separate upgrade endpoint) rejected: no backend
support and no behavioral difference.

### D4: Delivery semantics — retry install/upgrade, single-shot pre-uninstall

Install/upgrade run via the `trigger` module, which Forge retries up to 4 times
on a non-2xx or timeout. The handler therefore **throws** on an SDK result whose
`error` is present, so a transient backend failure is retried by the platform.

`preUninstall` is non-blocking, single invocation, 55-second budget, and its
return/throw is ignored by the platform. The handler treats both `200` (marked
uninstalled, or already-uninstalled idempotent no-op) and `404` (no tenant row —
nothing to clean up) as terminal success, and does not throw, since a throw buys
nothing and a `404` is an expected outcome when install never completed.
Rationale: aligns app behavior with the platform's own retry/timeout model and
with the backend's idempotent-uninstall and not-found contracts in
`uninstall-app`. Alternative (throw on `404`) rejected: no retry exists to
benefit, and it would only pollute logs.

### D5: Gateway — `principal` becomes optional

In `fit.Parse`, stop returning `ErrMissingClaims` when `principal` is empty.
Derive `accountId` from `principal` **only when present**; otherwise leave it
empty. Continue to require a resolvable cloudId (`context.cloudId` →
`app.apiBaseUrl` fallback), and continue to deny when no cloudId can be
resolved. Rationale (grounded in the Forge Remote FIT schema the user cited):
cloudId, not principal, identifies the tenant, and the FIT is Atlassian-signed
and already signature-verified upstream, so an absent principal is a legitimate
app-level invocation, not a spoof. The downstream effects are safe and already
implemented:

- Tenant routes carry no `x-issue-id`/`x-project-id`, so `Authorize` takes the
  skip-permission branch and returns identity headers with empty permissions;
  `POST/DELETE /tenants` never reads `x-project-permissions`.
- Permission-scoped routes with a user-less FIT get an empty `accountId`, so the
  Jira `permissions/check` call resolves zero permissions and the route is
  denied — fail-closed, no escalation.

Alternative (bypass auth for `/api/1/tenants` in Envoy, like the webhook routes)
rejected: it creates an unauthenticated tenant-write endpoint and drops the
signature-verified FIT that supplies the cloudId.

### D6: Function code layout and build

Function source lives at the app root alongside the manifest (Forge's default
`src/`), with `@forge/api` and `@smiskinext/smiski-ts` (`workspace:*`) added to
`app/package.json` `dependencies`. Forge's own bundler compiles the TypeScript
function and its dependencies on `deploy`/`tunnel`; the Vite build stays scoped
to the UI bundle and is untouched. Two `function` entries (one for the
install/upgrade handler, one for the pre-uninstall handler) keep the two
delivery semantics (D4) in separate, independently testable exports. Rationale:
Forge resolves function dependencies from the manifest package, and keeping the
UI build and the function build separate avoids coupling two toolchains.

### Manifest & flow

New modules: a `trigger` (events `avi:forge:installed:app`,
`avi:forge:upgraded:app`) → install/upgrade function; a `preUninstall` →
uninstall function; two `function` entries. The existing `meet-backend` remote
(already `operations: [compute]`) is reused. No new scopes or egress. Applying
the manifest requires `forge deploy` **then** `forge install --upgrade`; a
tunnel restart is not enough (per `app/AGENTS.md`). Administrator re-consent is
required only if the platform prompts for it (no scope/egress change here).

```mermaid
sequenceDiagram
    participant Forge as Forge platform
    participant Fn as Lifecycle function (@forge/api)
    participant SDK as smiski-ts register()/uninstall()
    participant GW as Envoy + gateway authz
    participant Tn as tenant service
    participant K as Kafka → meet/notification

    Note over Forge,Fn: install / upgrade (trigger, retried ≤4x)
    Forge->>Fn: avi:forge:installed|upgraded:app (+ FIT, no principal)
    Fn->>SDK: register({ path.version, body: payload })
    SDK->>GW: POST /api/1/tenants (Authorization: Bearer FIT)
    Note over GW: cloudId from app.apiBaseUrl; principal absent → accountId empty; no x-issue/project-id → skip permission check
    GW->>Tn: POST /tenants (X-Tenant-ID = cloudId)
    Tn-->>GW: 201 / 200 (tenant snapshot)
    GW-->>SDK: response
    SDK-->>Fn: result.data present
    alt result.error present
        Fn-->>Forge: throw → Forge retries
    else success
        Tn-)K: TenantInstalled
    end

    Note over Forge,Fn: uninstall (preUninstall, single-shot ≤55s)
    Forge->>Fn: preUninstall (+ FIT, no principal)
    Fn->>SDK: uninstall({ path.version })
    SDK->>GW: DELETE /api/1/tenants (Authorization: Bearer FIT)
    GW->>Tn: DELETE /tenants (X-Tenant-ID = cloudId)
    Tn-->>Fn: 200 (uninstalled / already) or 404 (unknown)
    Note over Fn: 200 and 404 both = success, no throw
    Tn-)K: TenantUninstalled (only on 200 active→uninstalled)
```

## Risks / Trade-offs

- **Making `principal` optional widens what parses** → Mitigation: cloudId is
  still mandatory and signature-verified; permission-scoped routes fail closed
  on an empty `accountId`; only the context-free tenant routes benefit. Covered
  by new parser tests for absent/empty principal and by the unchanged
  skip-permission path.
- **App is no longer function-free (departs from `app/AGENTS.md`)** → Accepted
  and documented in this design; the SDK-reuse requirement leaves no
  function-free option. Function build is isolated from the UI build (D6).
- **Manifest change forces `forge install --upgrade`** → Expected for any module
  change; no new scopes/egress, so re-consent is unlikely. Called out in tasks.
- **3-minute trigger delivery latency + up to 4 retries** (platform behavior) →
  Acceptable: tenant projection is not latency-critical, and the backend upsert
  is idempotent so repeated `register()` calls are safe.
- **Pre-uninstall 55s budget, no retry, throw ignored** → The single `DELETE` is
  fast and idempotent; treating `404` as success avoids a dead-end throw. If it
  is missed entirely, the tenant simply keeps its `ACTIVE` row until a future
  reconciliation — out of scope here.
- **`upgrade` payload's `permissions` is dropped** → No backend field exists for
  it and it does not affect tenant identity; documented in D3.

## Migration Plan

Additive. Deploy the new app modules/functions and the gateway parser change,
then `forge install --upgrade`. Rollback is code-only: reverting the gateway
restores the principal-required parse (no data change), and removing the app
modules stops lifecycle delivery. No schema or SDK changes are involved.

## Open Questions

None. Event set (install + upgrade + pre-uninstall), transport (`@forge/api`
SDK), delivery semantics, and the gateway `principal`-optional decision were
resolved during discovery against the Forge docs and the gateway source.
