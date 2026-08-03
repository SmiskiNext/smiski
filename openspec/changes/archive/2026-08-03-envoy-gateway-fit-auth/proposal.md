## Why

Backend services currently have no API gateway and no
authentication/authorization layer. Forge Custom UI calls backend services
directly with Forge Invocation Tokens (FIT), but services don't validate these
tokens or enforce Jira project permissions. This creates a security gap where
any request with a valid FIT can access any resource regardless of the user's
actual permissions in Jira. We need a centralized gateway that validates FIT
signatures, derives tenant/user identity, checks Jira project permissions via
the permissions/check API, and injects identity headers that backend services
can trust.

## What Changes

- **New**: Envoy proxy as API gateway (port 8080) handling all inbound traffic
  from Forge app
- **New**: Go gRPC authorization service implementing Envoy ext_authz protocol
- **New**: FIT signature validation via Atlassian JWKS with 1-hour cache
- **New**: Jira `permissions/check` integration using app system token to verify
  custom project permissions (`view-meeting`, `edit-meeting`)
- **New**: Valkey-based permission cache (15-minute TTL) with stale-while-error
  fallback
- **New**: Identity header injection (`X-Tenant-ID`, `X-Account-Id`,
  `X-Project-Permissions`) for downstream services
- **New**: Envoy HTTP response cache for GET requests
- **Modified**: Forge manifest to enable `appSystemToken` on all backend
  endpoints
- **Modified**: Forge UI (`forgeRemoteFetch.ts`) to inject issue/project context
  headers from Forge context
- **Modified**: Docker Compose stack to include Envoy, gateway service, and
  Valkey

## Capabilities

### New Capabilities

- `envoy-gateway-routing`: The Envoy edge — listens on 8080, validates the Forge
  Invocation Token (signature via Atlassian JWKS with 1-hour cache, issuer,
  audience, expiry), strips client-supplied trusted headers, delegates the
  authorization decision over gRPC `ext_authz`, caches GET responses, and routes
  `/api/1/tenants*`, `/api/1/meetings*`, `/api/1/notifications*` to their
  backend services. Answers _"is this a real Forge request, and where does it
  go?"_
- `permission-checking`: The authorization decision — the Go gRPC service
  implementing `envoy.service.auth.v3.Authorization`, which parses identity from
  the validated FIT (cloudId, accountId), resolves the authorization context
  from `X-Issue-Id`/`X-Project-Key`, verifies custom project permissions via
  Jira `POST /rest/api/3/permissions/check` with the app system token, caches
  results in Valkey (15-minute TTL, stale-while-error), and returns the
  allow/deny decision plus the `X-Tenant-ID`, `X-Account-Id`,
  `X-Project-Permissions` headers. Includes the two client-side contracts that
  feed it: the Forge UI injecting issue/project context headers, and the Forge
  manifest enabling `appSystemToken`. Answers _"what is this user allowed to do
  here?"_

## Impact

**New Components:**

- `services/gateway/` — Go gRPC service (new codebase)
- `services/docker/envoy/` — Envoy configuration + Lua scripts
- Valkey instance (if not already present in compose)

**Modified Components:**

- `app/manifest.yml` — endpoint auth configuration changes, requires
  `forge deploy` + `forge install --upgrade`
- `app/static/smiski-ui/src/api/forgeRemoteFetch.ts` — context header injection
  logic
- `services/docker/compose.yaml` — add envoy, gateway, valkey services

**Unchanged (Trust Model):**

- Backend services (`tenant`, `meet`, `notification`) already have
  `TenantFilter`, `AccountFilter`, `PermissionFilter` reading injected headers
- `@PreAuthorize` annotations on controllers already check
  `X-Project-Permissions` — no code changes needed
- `SecurityConfig` already configured with `permitAll()` (trusts gateway) — no
  changes

**Infrastructure:**

- Single point of failure: Envoy gateway becomes critical path for all requests
- Latency: +2-5ms per request (FIT validation + gRPC ext_authz + permission
  cache)
- Cache hit ratio: Expected >90% with 15-minute TTL for repeat issue access
- Dependency on Atlassian services: JWKS endpoint, Jira permissions API

**Deployment:**

- Requires Docker Compose changes for local dev
- AWS deployment: Gateway optimized for Lambda/Fargate (Go binary <20MB,
  cold-start <50ms)
- No K8s changes (out of scope)
