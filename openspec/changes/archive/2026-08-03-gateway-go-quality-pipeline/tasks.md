## 1. Repair pre-existing defects

- [x] 1.1 Untrack the compiled binary with
      `git rm --cached services/gateway/gateway`
- [x] 1.2 Add `services/gateway/gateway` to `.gitignore`
- [x] 1.3 Reformat the four `gofmt`-violating files:
      `internal/authz/service.go`, `internal/authz/server_test.go`,
      `internal/jira/client.go`, `internal/jira/types.go`
- [x] 1.4 Update `services/gateway/Dockerfile` base image from
      `golang:1.23-alpine` to `golang:1.25-alpine`
- [x] 1.5 Confirm `gofmt -l services/gateway` is empty and
      `docker build services/gateway` succeeds ← (verify: base image satisfies
      the `go 1.25.1` module requirement with no implicit toolchain download;
      `git ls-files services/gateway/gateway` is empty)

## 2. Resolve errcheck findings

- [x] 2.1 Handle the error from `cacheClient.Close()` in
      `cmd/gateway/main.go:66` by logging it during shutdown
- [x] 2.2 Handle the error from `defer resp.Body.Close()` in
      `internal/jira/client.go:80` using a deferred closure that logs the error
- [x] 2.3 Resolve the three unchecked error returns in
      `internal/cache/redis_test.go`
- [x] 2.4 Resolve the four unchecked error returns in
      `internal/jira/client_test.go` ← (verify: all 9 errcheck findings cleared;
      the two production fixes log rather than discard, and no test behaviour
      changed)

## 3. Toolchain and lint configuration

- [x] 3.1 Pin `go = "1.25.1"` in `.mise.toml`, matching the gateway `go.mod`
      directive
- [x] 3.2 Pin `golangci-lint = "latest"` in `.mise.toml`, following the existing
      pinning convention
- [x] 3.3 Create `.golangci.yml` using the v2 schema with `version: "2"`, the
      standard linter set plus `errcheck`, `govet`, `staticcheck`, `unused`,
      `ineffassign`, and a `formatters` block enabling `gofmt`
- [x] 3.4 Confirm `golangci-lint config verify` passes and `golangci-lint run`
      reports zero findings ← (verify: config loads under the pinned major
      version; a v1-shaped config would fail here)

## 4. Coverage backfill to clear the 80% gate

- [x] 4.1 Add unit tests for `internal/config` covering `Load` defaults,
      environment overrides, and an unparseable `CACHE_TTL` falling back to the
      default
- [x] 4.2 Add unit tests for `internal/authz/health.go` covering `Check`
      returning SERVING, `Check` returning NOT_SERVING on a cache ping failure,
      and `Watch`
- [x] 4.3 Add a unit test for `jira.Error.Unwrap`
- [x] 4.4 Add tests for the uncovered branches of `cache.GetStale`, currently at
      42.9%
- [x] 4.5 Add tests for the uncovered branches of `jira.doRequest`, currently at
      82.1%
- [x] 4.6 Confirm total statement coverage is at or above 83%, providing at
      least 3 points of margin above the gate ← (verify: measured from a single
      deduplicated profile via `go tool cover -func`; no exclusions applied;
      `cmd/gateway/main.go` still counted in the denominator)

## 5. Git hooks

- [x] 5.1 Add a `format-go` pre-commit command globbed to
      `services/gateway/**/*.go` running `gofmt -w` then
      `golangci-lint run --fix`, with `stage_fixed: true`
- [x] 5.2 Add a `build-go` pre-push command globbed to `services/gateway/**`
      running `go build ./...`
- [x] 5.3 Add a `test-go` pre-push command globbed to `services/gateway/**`
      running `go test ./...`, ordered after `build-go`
- [x] 5.4 Ensure the Go hook commands report missing tooling with installation
      guidance rather than passing silently, following the existing `gitleaks`
      pattern ← (verify: commit touching only non-Go files runs no Go step;
      commit touching a Go file reformats and re-stages it; push with a failing
      test is rejected)

## 6. Change detection

- [x] 6.1 Add a `gateway` filter matching `services/gateway/**` to the
      `detect-changes` composite action
- [x] 6.2 Expose `gateway` as a boolean output of the action
- [x] 6.3 Confirm the gateway is not added to the `services` matrix array or to
      `openapi_services` ← (verify: a gateway-only PR yields `gateway=true` with
      an empty `services` array, so no Gradle job targets the gateway; a
      `services/shared/` PR yields `gateway=false`)

## 7. CI validation workflows

- [x] 7.1 Add a `lint-gateway` job to `lint.yml`, gated on
      `needs.changes.outputs.gateway`, running `gofmt -l` as a non-mutating
      check and `golangci-lint run`
- [x] 7.2 Add `gateway` to the `changes` job outputs in `lint.yml`, `build.yml`,
      and `test.yml`
- [x] 7.3 Add a `build-gateway` job to `build.yml` running `go build ./...`
- [x] 7.4 Add a `test-gateway` job to `test.yml` running `go test ./...` with a
      coverage profile
- [x] 7.5 Implement the 80% statement coverage gate in `test-gateway`, computing
      the percentage from a single deduplicated profile, echoing the measured
      value, and failing below the threshold
- [x] 7.6 Upload the gateway coverage report as a workflow artifact with
      `if: always()`
- [x] 7.7 Add the Go jobs to the `needs` list of `lint-success`,
      `build-success`, and `test-success`, keeping the gate job names unchanged
      ← (verify: gate names unchanged so branch protection still applies; a
      gateway-only PR skips Gradle jobs yet the gates still pass; a Go job
      failure fails its gate)

## 8. Release pipeline

- [x] 8.1 Extend the release `gate` job to build and test the gateway module
      before any publishing
- [x] 8.2 Add a `publish-gateway` job building `services/gateway/Dockerfile`
      with SHA-pinned `docker/build-push-action`
- [x] 8.3 Tag the gateway image `ghcr.io/smiskinext/gateway` with both the
      resolved `X.Y.Z` and `latest`
- [x] 8.4 Authenticate to GHCR in `publish-gateway` and make the `release` job
      depend on it ← (verify: a gateway test failure blocks publishing of the
      passing Java images too; gateway image version matches the Java images; no
      tag or GitHub Release is created if the gateway image fails to build)

## 9. Scenario verification

- [x] 9.1 Verify a commit staging an unformatted Go file reformats and re-stages
      it
- [x] 9.2 Verify a commit staging no Go files runs no Go formatting step
- [x] 9.3 Verify a push with a Go compilation error is rejected before tests run
- [x] 9.4 Verify a push with a failing Go test is rejected
- [x] 9.5 Verify a push touching no gateway files runs neither `go build` nor
      `go test`
- [x] 9.6 Verify a gateway-only PR produces `gateway=true` and an empty
      `services` array
- [x] 9.7 Verify a `services/shared/`-only PR produces `gateway=false`
- [x] 9.8 Verify the lint job reports an unformatted file without modifying the
      working tree
- [x] 9.9 Verify an unchecked error return fails the lint job with its location
- [x] 9.10 Verify coverage below 80% fails the test job and reports the measured
      percentage
- [x] 9.11 Verify every package including the entrypoint contributes to the
      coverage denominator
- [x] 9.12 Verify the coverage percentage counts each statement exactly once
      across multiple package test binaries
- [x] 9.13 Verify the coverage artifact is uploaded when the gate fails
- [x] 9.14 Verify the workflows locally with `act -n` where feasible ← (verify:
      every scenario in the three delta specs has a corresponding confirmation;
      `openspec validate gateway-go-quality-pipeline` passes)
