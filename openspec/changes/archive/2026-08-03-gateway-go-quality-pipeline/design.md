## Context

`services/gateway` is a Go 1.25.1 gRPC service implementing Envoy's `ext_authz`
contract. It is the only non-JVM service in a monorepo whose entire quality
apparatus — lefthook hooks, four CI validation workflows, and the release
pipeline — is built around Gradle, pnpm, and Buf. The gateway is invisible to
all of it.

Measurements taken against the current working tree establish the baseline:

| Check                              | Result                             |
| ---------------------------------- | ---------------------------------- |
| `go build ./...`                   | passes                             |
| `go vet ./...`                     | clean                              |
| `go test ./...`                    | all packages pass                  |
| `gofmt -l`                         | 4 files violate                    |
| `golangci-lint run` (standard set) | **12 issues: 9 errcheck, 3 gofmt** |
| Statement coverage                 | **74.2% (178/240)**                |

Two errcheck findings are in production code, not tests:
`cmd/gateway/main.go:66` discards the error from `cacheClient.Close()`, and
`internal/jira/client.go:80` discards it from `defer resp.Body.Close()`. Neither
is reachable by `go vet`, which explains how they survived.

Three defects predate this change and would turn CI red the moment it is
enabled: the four `gofmt` violations, a Dockerfile pinning `golang:1.23-alpine`
against a `go.mod` requiring `go 1.25.1`, and a 25 MB compiled ELF binary staged
at `services/gateway/gateway`.

The gateway is owned by the in-progress `envoy-gateway-fit-auth` change (105/162
tasks). That change's `tasks.md` contains no hook or CI work, so this is a
genuine gap rather than a duplication.

## Goals / Non-Goals

**Goals:**

- Bring the gateway to parity with the Java services across hooks, CI, and
  release.
- Provision the Go toolchain from `.mise.toml`, honouring the existing
  requirement that CI never hardcodes tool versions.
- Enforce an 80% statement coverage gate with no exclusions.
- Publish `ghcr.io/smiskinext/gateway` only after build and tests pass.
- Repair the three pre-existing defects in the same change, so enabling
  enforcement does not immediately break `dev`.

**Non-Goals:**

- Integration tests, Forge deployment, manual verification, or documentation
  belonging to `envoy-gateway-fit-auth`.
- Kubernetes manifests for the gateway.
- Altering the Java JaCoCo thresholds (line ≥ 70% / branch ≥ 60%).
- Resolving the pre-existing `record` service drift between the CI specs and the
  implemented workflows.

## Decisions

### D1 — A separate `gateway` boolean output, not an entry in the `services` matrix

The `services` output feeds matrix jobs that invoke
`./services/gradlew -p services/<name>`. The gateway has no Gradle build, so
adding it to that array makes every matrix job fail on the gateway entry.

**Alternatives considered.** Adding a Gradle wrapper project for the gateway
purely to satisfy the matrix was rejected as ceremony that buys nothing. Making
the matrix polymorphic with per-entry conditionals was rejected because it makes
the matrix jobs read as two interleaved pipelines.

**Chosen:** a boolean `gateway` output alongside the existing `app`, `scripts`,
`proto`, and `docs` booleans, with dedicated jobs. This matches how every other
non-Gradle component in the repository is already wired.

### D2 — golangci-lint v2 configuration format

The mise registry resolves `golangci-lint` to 2.12.2. Version 2 moved formatters
out of `linters` into a top-level `formatters` block and requires an explicit
`version: "2"` key. A v1-shaped config fails to load.

The following configuration was verified with `golangci-lint config verify`
against 2.12.2 and produces the 12 findings tabulated above:

```yaml
version: '2'
linters:
    default: standard
    enable: [errcheck, govet, staticcheck, unused, ineffassign]
formatters:
    enable: [gofmt]
```

**Trade-off.** Pinning `golangci-lint = "latest"` in `.mise.toml` matches how
`gitleaks`, `lefthook`, `buf`, and `git-cliff` are already pinned, but a v3
release would break this config. `go` is pinned exactly (`1.25.1`) to match
`go.mod`, because a Go minor bump changes formatting and vet behaviour.

### D3 — Coverage gate at 80% with no exclusions

Per the user's explicit instruction, no file or package is excluded. Verified
arithmetic on the current tree:

| Scope                                 | Coverage            |
| ------------------------------------- | ------------------- |
| Everything (today)                    | 74.2% (178/240)     |
| Everything except `cmd/`              | 84.4% (178/211)     |
| **Ceiling: all but `main()` covered** | **87.9% (211/240)** |

Reaching 80% requires covering **14 additional statements**. The cheapest exact
path — `internal/config` (8) + `authz/health.go` (5) + `jira.Error.Unwrap` (1) —
lands on precisely 80.0% with zero margin, which would make the gate flip red on
any trivial addition. The plan therefore also covers the uncovered branches of
`cache.GetStale` (currently 42.9%) and `jira.doRequest` (82.1%), targeting
83–85% and a 3–5 point buffer.

**Accepted risk.** `cmd/gateway/main.go` contributes 29 permanently uncovered
statements — 12.1 points of the total. Each statement added to `main()` costs
roughly 0.33 points. Approximately 24 further lines of bootstrap code would
breach the gate even with everything else at 100%. This is a deliberate
consequence of the no-exclusion decision and is recorded here so the eventual
failure is diagnosed correctly rather than treated as a regression.

Note that the repository's JaCoCo setup takes the opposite position, excluding
"Bootstrap, `*Config`, `*JpaEntity`, and generated classes". The Go gate is
intentionally stricter.

### D4 — `docker/build-push-action` in a dedicated release job

The release matrix builds Java images with Spring Boot's `bootBuildImage`. The
gateway ships a hand-written multi-stage Dockerfile, so it needs a genuinely
different build path.

**Chosen:** a `publish-gateway` job parallel to the Gradle matrix, using
SHA-pinned `docker/build-push-action`, tagging both `X.Y.Z` and `latest` to
satisfy the existing synchronized-version requirement. The gateway also joins
the `gate` job so no image is published from unbuilt or untested code.

### D5 — Fix pre-existing defects within this change

Enabling enforcement without fixing the four `gofmt` violations turns `dev` red
on the first push. The Dockerfile version mismatch is latent: with
`GOTOOLCHAIN=auto` the 1.23 base image silently downloads a 1.25.1 toolchain at
build time, which is slow and fails outright in a network-restricted builder.
The binary is still only staged, never committed — `git rm --cached` plus a
`.gitignore` entry removes it cleanly now, whereas after a merge to `dev` it
would require history rewriting.

### D6 — Hook scope split between commit and push

`git-hooks` already mandates that pre-commit only auto-fixes and pre-push runs
verification. Go work follows that split: `gofmt -w` and
`golangci-lint run --fix` at pre-commit with `stage_fixed: true`;
`go build ./...` and `go test ./...` at pre-push. Running the full test suite at
commit time would violate the established contract and slow every commit.

## Risks / Trade-offs

**`main()` erodes the coverage headroom** → Recorded in D3. If the gate later
blocks legitimate bootstrap work, the resolution is to extract logic from
`main()` into a testable `run()` function rather than to weaken the gate.

**`golangci-lint = "latest"` may break on a v3 release** → Config is validated
by `golangci-lint config verify` in CI, so a format break surfaces as an
explicit configuration error rather than as confusing lint noise.

**Nine errcheck findings must be fixed before the gate can be enabled** → Seven
are in test files and resolve to `_ =` assignments. The two production findings
warrant real handling: log the error from `cacheClient.Close()` during shutdown,
and use a deferred closure that logs the error from `resp.Body.Close()`.

**Go jobs added to `*-success` gates change required checks** → The aggregate
gates already tolerate skipped dependencies, so PRs that do not touch the
gateway are unaffected. No branch protection reconfiguration is needed, since
the four gate job names are unchanged.

**Coverage measurement must be deterministic** → `go test ./...` writes one
profile per package; a naive `-coverpkg=./...` invocation double-counts
statements across test binaries and reports a misleading total. The gate must
compute the ratio from a single deduplicated profile via `go tool cover -func`,
whose reported total was confirmed to equal the hand-computed 74.2%.
