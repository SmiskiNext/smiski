## 1. FIT claim extraction

- [x] 1.1 Add an `Environment` struct to `internal/fit/parser.go` and read the
      `app.environment.id` claim into `Claims.App`
- [x] 1.2 Add a helper that returns the trailing slash-separated segment of an
      ARI, rejecting an empty result
- [x] 1.3 Expose the application identifier and environment identifier on
      `ParsedFIT`, returning a missing-claims error when either is absent ←
      (verify: both the single-segment and two-segment environment ARI forms
      yield the environment UUID, per design D4)

## 2. Jira request and response contract

- [x] 2.1 Rewrite `internal/jira/types.go` request models to
      `BulkPermissionsRequestBean` shape: `accountId`, `globalPermissions`,
      `projectPermissions[]` with `permissions`, `issues` and `projects` as
      `int64` slices, and no additional top-level members
- [x] 2.2 Rewrite the response models to `BulkPermissionGrants` shape:
      `globalPermissions` as a string slice and `projectPermissions[]` entries
      carrying a singular `permission` field, with no `hasPermission` boolean
- [x] 2.3 Decode `globalPermissions` and `projectPermissions` so an absent
      member is distinguishable from an empty one, and return an error when
      either required member is missing
- [x] 2.4 Rewrite `extractGrantedPermissions` to treat presence as the grant and
      collect identifiers from both response members
- [x] 2.5 Add permission-identifier construction and reverse mapping between the
      bare manifest key and the
      `ari:cloud:ecosystem::extension/{appId}/{environmentId}/static/{key}`
      form, ignoring and logging identifiers that were not requested ← (verify:
      published permissions are bare keys, so the existing
      `X-Project-Permissions` contract and `@PreAuthorize` checks are unchanged)

## 3. Rate limiting and error reporting

- [x] 3.1 Parse `Retry-After` on a `429` response and wait at least the
      advertised interval, bounded by the request context deadline ← (verify:
      the wait sits in the `CheckPermissions` retry loop under the caller's
      context, not inside `doRequest` under the per-attempt timeout, so the
      interval is honoured in full rather than truncated)
- [x] 3.2 Log a `400` naming an unrecognised permission distinctly from an
      ordinary permission denial, including the rejected identifier ← (verify: a
      malformed permission ARI is diagnosable from logs alone, per design D6)

## 4. Authorization service wiring

- [x] 4.1 Read `x-project-id` instead of `x-project-key` in
      `internal/authz/server.go` and carry it on `AuthzRequest`
- [x] 4.2 Parse issue and project identifiers as `int64`, failing the permission
      check rather than silently omitting an unparseable context ← (verify:
      presence is tracked from the header string, not the parsed value, so `0`
      is a valid identifier rather than an absence sentinel)
- [x] 4.3 Build the nested `projectPermissions` request, preferring the issue
      context when both are present
- [x] 4.4 Derive cache keys so an issue context and a project context with the
      same numeric value never collide
- [x] 4.5 Ensure a malformed Jira response follows the existing stale-cache
      fallback path rather than publishing an empty permission set ← (verify: a
      successful HTTP call with an unparseable body no longer yields empty
      permissions with a nil error — this is the root defect)
- [x] 4.6 Report an absent `x-forge-oauth-system` header as HTTP 500 with
      message "Missing system token" via an `ErrMissingSystemToken` sentinel
      that escapes before the stale-cache fallback ← (verify: a configuration
      fault is distinguishable from a Jira API failure, so it is never absorbed
      into an empty permission set)
- [x] 4.7 Map the 500 denied response to gRPC `codes.Internal`, leaving the
      `401` → `Unauthenticated` and `403` → `PermissionDenied` mappings
      unchanged ← (verify: the configuration fault is not reported to Envoy as
      an ordinary permission denial)

## 5. Gateway configuration

- [x] 5.1 Replace `x-project-key` with `x-project-id` in the `ext_authz`
      `allowed_headers` list in `services/docker/envoy/envoy.yaml`
- [x] 5.2 Validate the Envoy configuration with
      `docker run --rm -v "$(pwd)/services/docker/envoy:/etc/envoy:ro" envoyproxy/envoy:v1.36-latest --mode validate -c /etc/envoy/envoy.yaml`

## 6. Tests

- [x] 6.1 Replace the `httptest` fixtures in `internal/jira/client_test.go` so
      they emit the real `BulkPermissionGrants` shape instead of the
      `{key, hasPermission}` shape that encoded the defect ← (verify: response
      fixtures are raw JSON literals from the published Atlassian examples,
      never marshalled from the response structs, so a wrong tag cannot cancel
      out)
- [x] 6.2 Regression test: a well-formed Jira response granting both permissions
      yields `["view-meeting", "edit-meeting"]` rather than an empty slice
- [x] 6.3 Test: a response granting only `view-meeting` yields
      `["view-meeting"]`
- [x] 6.4 Test: a response with both members present but empty yields an empty
      permission set and is cached as a successful result
- [x] 6.5 Regression test: a response omitting a required member returns an
      error and triggers the stale-cache fallback instead of an empty permission
      set ← (verify: this is the test that would have caught the original silent
      failure)
- [x] 6.6 Test: the outgoing request body contains only the three schema-defined
      members, with numeric `issues` / `projects` values ← (verify: the body is
      captured on the wire and asserted as `map[string]any`, so a renamed
      request tag turns the test red)
- [x] 6.7 Test: the issue context is preferred when both context headers are
      present
- [x] 6.8 Test: a non-numeric `x-issue-id` or `x-project-id` fails the check
      rather than being dropped
- [x] 6.9 Test: permission identifiers are built correctly from both environment
      ARI forms, and a missing `app.environment.id` produces a missing-claims
      error
- [x] 6.10 Test: granted identifiers are mapped back to bare manifest keys, and
      an unrequested identifier is ignored
- [x] 6.11 Test: a `429` carrying `Retry-After` defers the retry, and an
      interval exceeding the deadline abandons the retry ← (verify: the
      advertised interval exceeds the quadratic backoff, so removing the
      `Retry-After` handling turns the test red)
- [x] 6.12 Test: issue and project cache keys with the same numeric value remain
      distinct
- [x] 6.13 Test: a response carrying unknown extra fields is still processed ←
      (verify: every scenario in the delta spec has a corresponding test)
- [x] 6.14 Test: a context identifier of `0` still scopes the request, so no
      unscoped query is sent
- [x] 6.15 Test: an empty permission set is cached as `[]` rather than `null`,
      and a repeat request for a no-permission user is a cache hit ← (verify:
      `Cache.Get` returning a non-nil empty slice satisfies the cache-hit check,
      so Jira is called exactly once, per design D3)
- [x] 6.16 Test: `Authorize` returns an error identifiable via
      `errors.Is(err, ErrMissingSystemToken)` and a nil result when the system
      token is absent ← (verify: the assertion is inverted from the previous
      "expected no error", so reverting the sentinel escape turns the test red)
- [x] 6.17 Test: a `CheckRequest` without `x-forge-oauth-system` yields HTTP
      500, message "Missing system token", gRPC `codes.Internal` and the
      `{"error","message"}` body shape ← (verify: both the 500 branch and the
      `codes.Internal` mapping are covered, so deleting either turns the test
      red)
- [x] 6.18 Test: `x-project-id` on a `CheckRequest` arrives as
      `AuthzRequest.ProjectID` ← (verify: reverting the header name to
      `x-project-key` or dropping the struct field turns the test red)
- [x] 6.19 Route client diagnostics through an injectable `logf` hook, mirroring
      the existing `wait` hook, so the D6 log is observable without redirecting
      the process-wide standard logger
- [x] 6.20 Test: a `400` naming an unrecognised permission emits the distinct
      message carrying the rejected identifier, and an ordinary `400` does NOT
      emit it ← (verify: deleting the log branch turns the first assertion red,
      and firing the log on every `400` turns the second red, so "distinctly" is
      pinned rather than merely "logs something", per design D6)
- [x] 6.21 Test: the retry wait receives the caller's context, asserted by a
      marker value and a deadline comparison ← (verify: wrapping the `c.wait`
      call in `context.WithTimeout(ctx, c.timeout)` turns the test red, so the
      2s truncation D7 exists to prevent cannot be reintroduced silently)

## 7. Verification

- [x] 7.1 Run `go test ./...`, `go vet ./...` and `gofmt -l internal cmd` in
      `services/gateway` ← (verify: `gofmt` reports no files; lefthook wires no
      Go formatter for `services/gateway`, so this is not caught automatically)
- [x] 7.2 Run `openspec validate fix-gateway-jira-permissions-contract --strict`
- [x] 7.3 Confirm no reference to `x-project-key` or `hasPermission` remains in
      `services/gateway` or `services/docker/envoy` ← (verify: the old contract
      is fully removed, not merely bypassed)
