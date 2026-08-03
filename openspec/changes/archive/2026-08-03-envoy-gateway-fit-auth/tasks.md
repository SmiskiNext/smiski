## 1. Gateway Service Setup

- [x] 1.1 Create `services/gateway/` directory structure
- [x] 1.2 Initialize Go module with `go mod init github.com/smiskinext/gateway`
- [x] 1.3 Add dependencies: go-control-plane v0.12.0, go-redis/v9, grpc v1.61.0
- [x] 1.4 Create `cmd/gateway/main.go` with gRPC server entrypoint
- [x] 1.5 Create `internal/config/config.go` for environment variable loading
- [x] 1.6 Create Dockerfile with multi-stage build (golang:1.23 → scratch)
- [x] 1.7 Add .dockerignore for Go build artifacts

## 2. FIT Token Parsing

- [x] 2.1 Create `internal/fit/parser.go` with FIT claims struct
- [x] 2.2 Implement JWT payload base64 decoding (no signature verification)
- [x] 2.3 Implement extractCloudID function parsing app.apiBaseUrl URL path
- [x] 2.4 Implement extractAccountID function reading principal claim
- [x] 2.5 Add error handling for missing or malformed claims
- [x] 2.6 Write unit tests for FIT parsing with valid/invalid tokens

## 3. Jira API Client

- [x] 3.1 Create `internal/jira/types.go` with PermissionsCheckRequest/Response
      structs
- [x] 3.2 Create `internal/jira/client.go` with CheckPermissions method
- [x] 3.3 Implement POST /rest/api/3/permissions/check with system token
      authentication
- [x] 3.4 Implement retry logic with exponential backoff (3 retries max)
- [x] 3.5 Implement 2-second timeout per request attempt
- [x] 3.6 Parse Jira response extracting granted permissions array
- [x] 3.7 Handle Jira API error responses (400, 429, 500, timeout)
- [x] 3.8 Write unit tests with mock HTTP client ← (verify: all Jira error
      scenarios handled, retries work correctly)

## 4. Valkey Cache Integration

- [x] 4.1 Create `internal/cache/redis.go` with Valkey client wrapper
- [x] 4.2 Implement Get method reading from Valkey
- [x] 4.3 Implement Set method writing to Valkey with TTL
- [x] 4.4 Implement GetStale method for stale-while-error fallback
- [x] 4.5 Implement cache key generation `perm:{cloudId}:{accountId}:{issueId}`
- [x] 4.6 Add connection error handling (bypass cache on failure)
- [x] 4.7 Write unit tests with miniredis mock ← (verify: cache hit/miss/stale
      scenarios work)

## 5. Authorization Service Logic

- [x] 5.1 Create `internal/authz/service.go` with AuthzService struct
- [x] 5.2 Implement Authorize method orchestrating FIT parse → cache check →
      Jira call
- [x] 5.3 Implement permission check with issueId context
- [x] 5.4 Implement permission check with projectKey context
- [x] 5.5 Implement cache miss logic calling Jira API
- [x] 5.6 Implement stale-while-error fallback when Jira fails
- [x] 5.7 Implement skip permission check when no context headers present
- [x] 5.8 Add logging for cache hits/misses and Jira calls
- [x] 5.9 Write unit tests with mock Jira client and cache ← (verify: all
      authorization scenarios return correct permissions)

## 6. gRPC ext_authz Server

- [x] 6.1 Create `internal/authz/server.go` implementing Authorization interface
- [x] 6.2 Implement Check method extracting headers from CheckRequest
- [x] 6.3 Extract Authorization, X-Issue-Id, X-Project-Key, x-forge-oauth-system
      headers
- [x] 6.4 Call AuthzService.Authorize with extracted values
- [x] 6.5 Build OkHttpResponse with X-Tenant-ID, X-Account-Id,
      X-Project-Permissions headers
- [x] 6.6 Build DeniedHttpResponse with 403 status and JSON error body
- [x] 6.7 Set gRPC status code (OK or PERMISSION_DENIED) in CheckResponse
- [x] 6.8 Add panic recovery middleware
- [x] 6.9 Implement gRPC health check protocol
- [x] 6.10 Add logging for each Check invocation with key details
- [x] 6.11 Wire up server in main.go listening on port 9001 ← (verify: gRPC
      server starts, health check responds)

## 7. Envoy Configuration

- [x] 7.1 Create `services/docker/envoy/` directory
- [x] 7.2 Create `envoy.yaml` with static_resources configuration
- [x] 7.3 Configure listener on port 8080
- [x] 7.4 Add jwt_authn filter with Atlassian JWKS configuration
- [x] 7.5 Set jwt_authn issuer to `forge/invocation-token` (corrected from
      `https://forge.atlassian.com` — see note below)
- [x] 7.6 Set jwt_authn audience to Forge app ID ARI
- [x] 7.7 Configure remote JWKS with 1-hour cache duration
- [x] 7.8 Set payload_in_metadata to `fit_payload`
- [x] 7.9 Create `envoy/lua/extract_claims.lua` parsing FIT metadata
- [x] 7.10 Add Lua filter extracting cloudId and accountId to request headers
- [x] 7.11 Add ext_authz filter with gRPC service configuration
- [x] 7.12 Configure ext_authz to call gateway:9001 with 2-second timeout
- [x] 7.13 Configure ext_authz allowed request headers (authorization,
      x-issue-id, x-project-key, x-forge-oauth-system)
- [x] 7.14 Configure ext_authz allowed response headers (x-tenant-id,
      x-account-id, x-project-permissions)
- [x] 7.15 Add router filter
- [x] 7.16 Configure route `/api/1/tenants*` to tenant:8081 cluster
- [x] 7.17 Configure route `/api/1/meetings*` to meet:8082 cluster
- [x] 7.18 Configure route `/api/1/notifications*` to notification:8083 cluster
- [x] 7.19 Define gateway_grpc_cluster with http2_protocol_options
- [x] 7.20 Define forge_jwks_cluster with TLS transport
- [x] 7.21 Define tenant_cluster, meet_cluster, notification_cluster ← (verify:
      envoy.yaml syntax valid, all clusters defined)

> **7.5 deviation — FIT issuer (resolved).** The proposal/design originally
> specified issuer `https://forge.atlassian.com`. Atlassian's official Forge
> Remote essentials docs and this repo's own
> `requirements/references/forgeapp-remote-backend.md` (§5.1/5.2) both state the
> FIT `iss` claim is the literal string `forge/invocation-token`. Using the
> specified value would reject **every** genuine FIT with 401. `envoy.yaml` uses
> `forge/invocation-token`; `design.md` D7 and the issuer requirement (now in
> the `envoy-gateway-routing` spec) have been corrected to match.

> **7.14 note — allowed_upstream_headers not set.** The gateway returns its
> identity headers via the ext_authz gRPC `OkHttpResponse`, and
> `allowed_upstream_headers` only applies to the **HTTP** ext_authz service (for
> gRPC, all `OkHttpResponse` headers are applied). Verified end-to-end:
> `x-tenant-id`, `x-account-id`, and `x-project-permissions` all reach the
> upstream without that field.

## 8. Docker Compose Integration

- [ ] 8.1 Update `services/docker/compose.yaml` adding envoy service
- [ ] 8.2 Add gateway service with build context `../gateway`
- [ ] 8.3 Add valkey service if not already present
- [ ] 8.4 Configure envoy port mapping 8080:8080
- [ ] 8.5 Configure envoy volumes mounting envoy.yaml and lua scripts
- [ ] 8.6 Configure gateway environment variables (REDIS_ADDR, JIRA_API_BASE,
      CACHE_TTL)
- [ ] 8.7 Configure gateway port 9001:9001
- [ ] 8.8 Add depends_on: gateway for envoy service
- [ ] 8.9 Add depends_on: valkey for gateway service
- [ ] 8.10 Ensure all services on same backend network ← (verify: compose up
      succeeds, all services healthy)

## 9. Forge UI Changes

- [x] 9.1 Open `app/static/smiski-ui/src/api/forgeRemoteFetch.ts`
- [x] 9.2 Add `import { view } from '@forge/bridge'` if not present
- [x] 9.3 Add `const context = await view.getContext()` before request
- [x] 9.4 Extract `context.extension?.issue?.id` to X-Issue-Id header
- [x] 9.5 Extract `context.extension?.project?.key` to X-Project-Key header
- [x] 9.6 Handle context fetch timeout gracefully (log warning, continue)
- [x] 9.7 Convert numeric issue IDs to strings ← (verify: headers injected in
      browser network tab)

## 10. Forge Manifest Changes

- [x] 10.1 Open `app/manifest.yml`
- [x] 10.2 Locate `remotes` section and verify `baseUrl` points to Envoy gateway
- [x] 10.3 Locate all `endpoint` definitions for backend routes
- [x] 10.4 Add `auth.appSystemToken.enabled: true` to each backend endpoint
- [x] 10.5 Verify endpoint paths unchanged (e.g., `/api/1/meetings:instant`)
- [x] 10.6 Verify endpoint operations unchanged (e.g., `compute`) ← (verify:
      manifest passes forge lint)

## 11. Unit Tests

- [x] 11.1 Test FIT parser with valid token containing all claims
- [x] 11.2 Test FIT parser with missing app.apiBaseUrl claim
- [x] 11.3 Test FIT parser with missing principal claim
- [x] 11.4 Test FIT parser with malformed base64 payload
- [x] 11.5 Test cloudId extraction from various app.apiBaseUrl formats
- [x] 11.6 Test Jira client with successful response
- [x] 11.7 Test Jira client with partial permissions granted
- [x] 11.8 Test Jira client with no permissions granted
- [x] 11.9 Test Jira client with timeout and retry logic
- [x] 11.10 Test Jira client with HTTP 429 rate limit response
- [x] 11.11 Test Jira client with HTTP 500 error
- [x] 11.12 Test cache Get with existing key
- [x] 11.13 Test cache Get with missing key
- [x] 11.14 Test cache Set with TTL
- [x] 11.15 Test cache GetStale for expired keys
- [x] 11.16 Test cache connection failure bypass
- [x] 11.17 Test AuthzService with cache hit scenario
- [x] 11.18 Test AuthzService with cache miss triggering Jira call
- [x] 11.19 Test AuthzService with stale cache on Jira failure
- [x] 11.20 Test AuthzService with no context headers (skip permission check)
- [x] 11.21 Test gRPC server Check with valid authorization
- [x] 11.22 Test gRPC server Check with authorization failure
- [x] 11.23 Test gRPC server Check with missing system token ← (verify: all unit
      tests pass with >80% coverage)
