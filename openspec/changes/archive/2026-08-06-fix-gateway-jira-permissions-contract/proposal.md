## Why

The Go authorization gateway calls Jira's `POST /rest/api/3/permissions/check`
with a request and response shape that does not exist in the Jira Cloud REST API
v3 contract. Both `BulkPermissionsRequestBean` and `BulkPermissionGrants`
declare `additionalProperties: false`, so the gateway's flat `permissions` /
`issueId` / `projectKey` request fields are not part of the schema, and its
expected `[{key, hasPermission}]` response shape does not match the actual
`globalPermissions: [string]` /
`projectPermissions: [{permission, issues, projects}]`.

The response mismatch fails silently: Go's JSON decoder ignores unknown fields,
so `extractGrantedPermissions` filters on a `hasPermission` boolean that is
never populated and returns an empty slice with a `nil` error. Every
authenticated request therefore receives an empty `X-Project-Permissions`
header, every `@PreAuthorize("hasAuthority('view-meeting'))` check fails, and
the backend returns 403 for all authenticated routes.

The existing unit tests encode the same incorrect schema in their `httptest`
fixtures, so the whole suite passes while production is fully broken. The
`permission-checking` spec also documents the incorrect request and response
shapes, which is how the defect propagated into the implementation in the first
place. Fixing the code without fixing the spec would leave the root source of
the error in place.

## What Changes

- Rewrite the Jira permission-check request payload to the documented
  `BulkPermissionsRequestBean` shape, nesting permission keys and context inside
  `projectPermissions[]`.
- Rewrite the Jira permission-check response model to the documented
  `BulkPermissionGrants` shape: `globalPermissions` is an array of strings, and
  `projectPermissions[]` entries carry a singular `permission` field with no
  `hasPermission` boolean. Presence in the response means the permission is
  granted.
- **BREAKING**: Replace the `X-Project-Key` authorization context header with
  `X-Project-Id`. Jira's `projectPermissions.projects` accepts numeric project
  IDs (`int64`), not project keys. The Forge context already exposes
  `project.id`, so no additional Jira lookup is required.
- Build the fully-qualified custom permission key as
  `ari:cloud:ecosystem::extension/[appId]/[environmentId]/static/[key]` from FIT
  claims, because Jira rejects bare manifest keys such as `view-meeting` with
  `400 Unrecognized permission`.
- Extend FIT claim parsing to read `app.environment.id`, required to build the
  permission ARI.
- Treat a structurally invalid Jira response as an API failure that triggers the
  stale-cache fallback, instead of silently degrading to an empty permission
  set.
- Honour the `Retry-After` header on `429` responses, which the current fixed
  quadratic backoff ignores.
- Replace the test fixtures that currently encode the incorrect schema, so the
  suite fails against a wrong contract rather than confirming it.

## Capabilities

### New Capabilities

None. This change corrects existing behaviour and introduces no new capability.

### Modified Capabilities

- `permission-checking`: Corrects the documented Jira request and response
  contract, replaces the `projectKey` context with a numeric `projectId`,
  specifies the permission-key ARI format, requires explicit failure on a
  malformed Jira response, and requires `Retry-After` to be honoured.

## Impact

**Go gateway** (`services/gateway/`)

- `internal/jira/types.go` — request and response models rewritten
- `internal/jira/client.go` — permission extraction, ARI construction,
  `Retry-After` handling
- `internal/authz/service.go` — nested request construction, `x-project-id`
  header, `int64` parsing, cache key
- `internal/fit/parser.go` — `app.environment.id` claim, ARI derivation
- `internal/jira/client_test.go`, `internal/authz/service_test.go` — fixtures
  corrected to the real Jira schema

**Gateway configuration**

- `services/docker/envoy/envoy.yaml` — `ext_authz` allowlist gains
  `x-project-id`

**Specification**

- `openspec/specs/permission-checking/spec.md` — request/response scenarios,
  context header, permission key format, error handling
- The delta spec restates the frontend requirement "Inject issue context headers
  from Forge context" with `X-Project-Id` as the forward contract for the
  separate transport change. No frontend code changes in this change; `app/` is
  untouched.

**Not in scope (tracked separately)**

The `x-forge-oauth-system` system token never reaches the gateway, because the
Custom UI calls the backend with `requestRemote`, which does not forward OAuth
tokens, and `forgeRemoteFetch.ts` does not inject the `X-Issue-Id` /
`X-Project-Id` context headers the spec requires. That is an independent blocker
requiring a transport change (`invokeRemote`) plus frontend header injection.
**This change alone does not restore end-to-end authorization**; it corrects the
Jira contract and removes the silent-failure mode. Downstream services and the
`X-Project-Permissions` header contract are unaffected and stay as they are.
