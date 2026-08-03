## Why

`services/gateway` is the repository's only Go service and is currently excluded
from every automated quality control in the monorepo. No Git hook formats,
lints, or tests it; no CI workflow builds, tests, or measures its coverage; and
no release job publishes its container image. The three Java services, the Forge
app, the scripts CLI, and the proto module are all covered — the gateway is the
single unguarded component.

The gap is not theoretical. Four tracked Go files already violate `gofmt`, the
Dockerfile pins `golang:1.23` while `go.mod` requires `go 1.25.1`, and a 25 MB
compiled ELF binary is staged for commit at `services/gateway/gateway`. These
defects reached the repository precisely because nothing checks Go code.

## What Changes

- Pin `go` and `golangci-lint` in `.mise.toml` so CI provisions the Go toolchain
  from the same source as every other tool.
- Add a repository `.golangci.yml` (golangci-lint v2 configuration format).
- Extend the pre-commit hook to run `gofmt -w` and `golangci-lint run --fix` on
  staged Go files, re-staging fixed output.
- Extend the pre-push hook to run `go build ./...` and `go test ./...` when a
  push touches `services/gateway/**`.
- Add a `gateway` boolean output to the `detect-changes` composite action. **The
  gateway is deliberately excluded from the existing `services` matrix output**,
  because that matrix drives `./services/gradlew -p services/<name>` and the
  gateway is not a Gradle project.
- Add Go jobs to the lint, build, and test workflows, each gated on the new
  `gateway` output and wired into the existing per-workflow success gates.
- Enforce a Go coverage gate at **80% of statements with no file or package
  exclusions**, measured across the whole module.
- Add the gateway to the release quality gate and publish
  `ghcr.io/smiskinext/gateway` via a dedicated `docker/build-push-action` job,
  separate from the Gradle `bootBuildImage` matrix.
- Correct the three pre-existing defects: reformat the four `gofmt`-violating
  files, align the Dockerfile Go version with `go.mod`, and untrack the compiled
  binary while adding it to `.gitignore`.
- Add unit tests for `internal/config`, `internal/authz/health.go`,
  `jira.Error.Unwrap`, and the uncovered branches of `cache.GetStale` so the
  module clears the 80% gate with margin.

## Capabilities

### New Capabilities

None. Go quality enforcement is an extension of the repository's existing hook,
CI, and release capabilities rather than a new behavior domain.

### Modified Capabilities

- `git-hooks`: The pre-commit auto-fix requirement and the pre-push
  typecheck/build requirement are both extended to cover Go sources, which the
  current text enumerates exhaustively without mentioning Go.
- `ci-pipeline`: The change-detection requirement gains a `gateway` output; a
  new Go validation requirement covers lint, build, and test; the coverage gate
  requirement is extended with the Go threshold alongside the existing JaCoCo
  thresholds; and the aggregate success gates take the Go jobs as dependencies.
- `release-pipeline`: The release quality gate requirement is extended to cover
  the gateway, and the container image publishing requirement is extended to
  describe Dockerfile-based publishing for a non-Gradle service.

## Impact

**Configuration**

- `.mise.toml` — pin `go`, `golangci-lint`
- `.golangci.yml` — new
- `.gitignore` — untrack `services/gateway/gateway`
- `lefthook.yml` — `format-go` (pre-commit), `build-go` + `test-go` (pre-push)

**CI/CD**

- `.github/actions/detect-changes/action.yml` — `gateway` filter and output
- `.github/workflows/lint.yml` — `lint-gateway` job
- `.github/workflows/build.yml` — `build-gateway` job
- `.github/workflows/test.yml` — `test-gateway` job with the coverage gate
- `.github/workflows/release.yml` — gateway in `gate`, new `publish-gateway`

**Source**

- `services/gateway/Dockerfile` — `golang:1.23-alpine` → `golang:1.25-alpine`
- `internal/authz/service.go`, `internal/authz/server_test.go`,
  `internal/jira/client.go`, `internal/jira/types.go` — `gofmt`
- New tests under `internal/config`, `internal/authz`, `internal/cache`,
  `internal/jira`

**Dependencies**

- New CI tool: `golangci-lint` (via mise)
- New CI action: `docker/build-push-action` (SHA-pinned per existing hardening
  requirements)

**Risk**

`cmd/gateway/main.go` holds 29 uncovered statements — 12.1 percentage points of
the module total — and is not meaningfully unit-testable. With no exclusions the
practical coverage ceiling is 87.9%. Growth in `main()` consumes headroom
against the 80% gate at roughly 0.33 points per statement.

**Out of scope**

Integration tests, Forge deployment, manual verification, and documentation for
the in-progress `envoy-gateway-fit-auth` change; Kubernetes manifests for the
gateway; changes to Java coverage thresholds; and the pre-existing `record`
service drift between the CI specs and the implemented workflows.
