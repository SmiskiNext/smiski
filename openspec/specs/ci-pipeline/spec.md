# ci-pipeline Specification

## Purpose

Defines the modular GitHub Actions CI pipeline, split across four validation
workflows (lint, build, test, security). A shared composite action provides
change detection and path filtering to run only the jobs relevant to each PR.
Branch protection requires four per-workflow success gates.

## Requirements

### Requirement: Workflow triggers

The CI workflow SHALL run on `pull_request` events targeting `dev` or `main`,
and on `push` events to `dev` or `main`. It SHALL NOT run on pushes to other
branches (those are validated via their PRs).

#### Scenario: Pull request opened against dev

- **WHEN** a pull request targeting `dev` is opened, synchronized, or reopened
- **THEN** the CI workflow starts and reports a status check on the PR

#### Scenario: Push to protected branch

- **WHEN** a commit is pushed to `dev` or `main`
- **THEN** the CI workflow runs against that ref

#### Scenario: Push to an unrelated feature branch

- **WHEN** a commit is pushed to a branch other than `dev` or `main` with no
  open PR
- **THEN** the CI workflow does not run

### Requirement: Change detection and path filtering

Change detection SHALL be provided by a reusable composite action at
`.github/actions/detect-changes` that every validation workflow invokes as a
step. The action SHALL classify changed paths and expose: a `services` output
containing a JSON array of the changed backend service names for use as a job
matrix, boolean outputs `app`, `scripts`, `proto`, `docs`, and `gateway`, and an
`openapi_services` output listing the subset of changed services that emit an
OpenAPI spec (`tenant`, `meet`, `record`, `notification`). Each service entry
SHALL be selected when its own directory changes OR when any shared input
changes: `services/shared/**`, `services/proto/**`, `build-logic/**`, or
`gradle/libs.versions.toml`. The `gateway` output SHALL be selected when
`services/gateway/**` changes. Because the gateway is not a Gradle project, it
SHALL NOT appear in the `services` matrix output, which drives Gradle-based
jobs. Each component job SHALL run only when its corresponding output is truthy
or its matrix is non-empty.

#### Scenario: Single service changed

- **WHEN** a PR modifies files only under `services/tenant/`
- **THEN** the `services` output is `["tenant"]` and the build/test matrix runs
  only for `tenant`

#### Scenario: Shared code fans out to all services

- **WHEN** a PR modifies files under `services/shared/`, `build-logic/`, or
  `gradle/libs.versions.toml`
- **THEN** the `services` output contains every backend service and the
  build/test matrix runs for all of them

#### Scenario: Proto changed

- **WHEN** a PR modifies files under `services/proto/`
- **THEN** the `proto` output is `true` and the `services` output fans out to
  all services

#### Scenario: Gateway changed

- **WHEN** a PR modifies files only under `services/gateway/`
- **THEN** the `gateway` output is `true` and the Go jobs run

#### Scenario: Gateway is absent from the Gradle matrix

- **WHEN** a PR modifies files only under `services/gateway/`
- **THEN** the `services` output is an empty array, so no Gradle matrix job
  attempts to build the gateway

#### Scenario: Gateway is unaffected by shared Java inputs

- **WHEN** a PR modifies only `services/shared/` or `build-logic/`
- **THEN** the `gateway` output is `false`, because the gateway shares no code
  with the Gradle builds

#### Scenario: Only docs changed

- **WHEN** a PR modifies only Markdown or root config files
- **THEN** the `docs` output is `true`, the `services` output is an empty array,
  and the backend matrix jobs are skipped

#### Scenario: OpenAPI subset includes notification

- **WHEN** a PR changes `services/notification/` only
- **THEN** `notification` appears in both `services` and `openapi_services`

### Requirement: Toolchain provisioning from `.mise.toml`

Every job that needs project tooling SHALL install it via `jdx/mise-action`
reading the repository `.mise.toml`, rather than hardcoding tool versions in the
workflow. The workflow SHALL NOT duplicate the Java, Node, pnpm, Buf, gitleaks,
Go, or Go lint tool version numbers. The pinned Go version SHALL match the
version required by the gateway `go.mod`.

#### Scenario: Tool versions match local configuration

- **WHEN** any CI job provisions its toolchain
- **THEN** the installed Java, Node, pnpm, Buf, gitleaks, Go, and Go lint tool
  versions are those resolved from `.mise.toml`

#### Scenario: Adding a tool pin flows to CI without workflow edits

- **WHEN** a new tool is added to `.mise.toml`
- **THEN** CI jobs can use it without editing version numbers in the workflow

#### Scenario: Go version is consistent with the module

- **WHEN** the Go version resolved in CI is compared with the `go` directive in
  the gateway `go.mod`
- **THEN** the two agree, so CI does not implicitly download a second toolchain

### Requirement: Backend validation job

For changes selecting one or more backend services, the build workflow SHALL run
a per-service matrix job that compiles each selected service with
`./services/gradlew -p services/<name> assemble`, without executing the `test`
or `integrationTest` source sets. The job SHALL fail if compilation fails.

#### Scenario: Service compiles

- **WHEN** the build matrix runs `assemble` for a selected service and
  compilation succeeds
- **THEN** that matrix entry succeeds

#### Scenario: Compilation error

- **WHEN** `assemble` fails to compile a selected service
- **THEN** that matrix entry fails and the build gate fails

#### Scenario: Build does not run tests

- **WHEN** the build workflow runs for a service
- **THEN** no `test` or `integrationTest` tasks execute in the build workflow

### Requirement: Backend test job

For changes selecting one or more backend services, the test workflow SHALL run
a per-service matrix job that executes
`./services/gradlew -p services/<name> test integrationTest` on a Docker-enabled
runner, covering the fast `test` source set and the container-backed
`integrationTest` source set. The job SHALL fail if any test fails.

#### Scenario: Service tests pass

- **WHEN** the test matrix runs `test integrationTest` for a selected service
  and all tests pass
- **THEN** that matrix entry succeeds

#### Scenario: A test fails

- **WHEN** any unit, ArchUnit, or integration test fails for a selected service
- **THEN** that matrix entry fails and the test gate fails

#### Scenario: Testcontainers run without extra infra

- **WHEN** the `integrationTest` source set executes on the runner
- **THEN** Testcontainers provisions its own containers using the runner's
  preinstalled Docker, with no manually declared service containers

### Requirement: Coverage gate

The test workflow SHALL enforce the JaCoCo coverage gate
(`jacocoTestCoverageVerification`, line ≥ 70% / branch ≥ 60%) per selected
service and publish the JaCoCo report as a build artifact. The CI-enforced
thresholds SHALL match those defined in the Gradle convention plugin and SHALL
NOT be lowered by the workflow.

For the gateway, the test workflow SHALL enforce a statement coverage gate of at
least 80% measured across the entire Go module, with no file or package excluded
from the measurement. Coverage SHALL be computed from a single deduplicated
coverage profile so that no statement is counted more than once, and the
measured percentage SHALL be reported in the job output. The gateway coverage
report SHALL be published as a build artifact.

#### Scenario: Coverage meets thresholds

- **WHEN** measured line coverage ≥ 70% and branch coverage ≥ 60% for a service
- **THEN** the coverage gate passes for that service

#### Scenario: Coverage below thresholds

- **WHEN** measured coverage for a service is below the configured thresholds
- **THEN** the coverage gate fails the test job for that service

#### Scenario: Coverage report is retained

- **WHEN** the test job completes, whether the coverage gate passed or failed
- **THEN** the JaCoCo HTML/XML report is uploaded as a workflow artifact

#### Scenario: Gateway coverage meets the threshold

- **WHEN** measured statement coverage for the gateway module is at or above 80%
- **THEN** the gateway coverage gate passes

#### Scenario: Gateway coverage below the threshold

- **WHEN** measured statement coverage for the gateway module is below 80%
- **THEN** the gateway coverage gate fails the test job and the measured
  percentage is reported

#### Scenario: No package is excluded from gateway measurement

- **WHEN** the gateway coverage percentage is computed
- **THEN** every package in the module contributes to the denominator, including
  the entrypoint package, and no exclusion filter is applied

#### Scenario: Statements are not double-counted

- **WHEN** the coverage profile is produced by multiple package test binaries
- **THEN** the reported percentage counts each statement exactly once

#### Scenario: Gateway coverage report is retained

- **WHEN** the gateway test job completes, whether its coverage gate passed or
  failed
- **THEN** the gateway coverage report is uploaded as a workflow artifact

### Requirement: OpenAPI drift check

The test workflow SHALL, for each changed service in `openapi_services`,
regenerate the per-service OpenAPI spec, fail if the committed
`services/<name>/openapi.yaml` differs from the regenerated output, and lint the
specs with Redocly. When any service in `openapi_services` changed, the workflow
SHALL additionally regenerate the combined `services/openapi.yaml` by joining
the per-service specs, fail if the committed combined document differs from the
regenerated output, and lint the combined document with Redocly.

#### Scenario: Committed specs match generated output

- **WHEN** regenerating produces no change to the committed `openapi.yaml` and
  Redocly lint passes
- **THEN** the OpenAPI drift check passes

#### Scenario: Committed specs are stale

- **WHEN** regenerating produces a diff against the committed `openapi.yaml`
- **THEN** the check fails with a message instructing the author to run
  `pnpm run openapi` locally and commit the result

#### Scenario: Spec violates lint rules

- **WHEN** Redocly lint reports an error against a generated spec
- **THEN** the OpenAPI drift check fails

#### Scenario: Combined spec is stale

- **WHEN** regenerating the combined `services/openapi.yaml` produces a diff
  against the committed combined document
- **THEN** the check fails with a message instructing the author to run
  `pnpm run openapi` locally and commit the result

#### Scenario: Combined spec violates lint rules

- **WHEN** Redocly lint reports an error against the generated combined
  `services/openapi.yaml`
- **THEN** the OpenAPI drift check fails

### Requirement: Forge app validation job

For changes touching `app/`, the lint workflow SHALL run Biome check on the app,
and the build workflow SHALL run `tsc --noEmit` on the app root, build the
nested `static/hello-world` UI, and run `forge lint` against `manifest.yml`.
Neither workflow SHALL deploy or install the Forge app.

#### Scenario: App passes all checks

- **WHEN** Biome (lint workflow) and `tsc`, the `static/hello-world` build, and
  `forge lint` (build workflow) all succeed
- **THEN** the app jobs succeed

#### Scenario: UI build fails

- **WHEN** the `static/hello-world` react-scripts build fails
- **THEN** the build workflow app job fails

#### Scenario: Manifest is invalid

- **WHEN** `forge lint` reports an error against `manifest.yml`
- **THEN** the build workflow app job fails

#### Scenario: No deployment occurs

- **WHEN** the app jobs run
- **THEN** no `forge deploy` or `forge install` command is executed and no Forge
  credentials are required

### Requirement: Scripts validation job

For changes touching `scripts/`, the lint workflow SHALL run Biome check and the
build workflow SHALL run `tsc --noEmit` for the CLI package.

#### Scenario: Scripts pass lint and typecheck

- **WHEN** Biome (lint workflow) and `tsc --noEmit` (build workflow) both
  succeed
- **THEN** the scripts jobs succeed

#### Scenario: Type error in scripts

- **WHEN** `tsc --noEmit` reports a type error in `scripts/`
- **THEN** the build workflow scripts job fails

### Requirement: Proto validation job

For changes touching `services/proto/`, the lint workflow SHALL run `buf lint`
and `buf breaking` comparing against the `dev` branch.

#### Scenario: Proto lint and breaking pass

- **WHEN** `buf lint` reports no issues and `buf breaking` finds no breaking
  changes against `dev`
- **THEN** the proto job succeeds

#### Scenario: Breaking change detected

- **WHEN** `buf breaking` detects a breaking change against `dev`
- **THEN** the proto job fails

### Requirement: Security scan job

Secret scanning SHALL be a standalone `security` workflow that always runs a
`gitleaks detect` scan regardless of which components changed and independent of
the change-detection matrix, using the repository `.gitleaks.toml`
configuration.

#### Scenario: No secrets present

- **WHEN** the gitleaks scan finds no secrets
- **THEN** the security workflow succeeds

#### Scenario: Secret detected

- **WHEN** gitleaks detects a secret in the repository history
- **THEN** the security workflow fails

#### Scenario: Security scan is component-independent

- **WHEN** a PR changes only documentation
- **THEN** the security workflow still runs

### Requirement: Docs validation job

The lint workflow SHALL, for changes touching documentation and root config
files, run markdownlint on Markdown files and Prettier in check mode on
`md/json/toml/yaml/yml/sh/sql` files.

#### Scenario: Docs are well-formatted

- **WHEN** markdownlint and Prettier `--check` both pass
- **THEN** the docs job succeeds

#### Scenario: Formatting violation

- **WHEN** Prettier `--check` reports an unformatted file
- **THEN** the docs job fails

#### Scenario: SQL is checked by Prettier

- **WHEN** a `.sql` file under `services/**/db/migration/` is not formatted per
  `prettier-plugin-sql`
- **THEN** the docs job fails

### Requirement: Aggregate success gate

Each validation workflow SHALL include its own aggregate success gate
(`lint-success`, `build-success`, `test-success`, `security-success`) that
depends on the workflow's jobs, including the Go jobs where present, runs even
when some dependencies are skipped, and succeeds only if no dependency failed or
was cancelled. The gate job names SHALL remain unchanged so that existing branch
protection continues to apply. Branch protection is expected to require these
four per-workflow checks.

#### Scenario: All run jobs succeed

- **WHEN** every dependency job result in a workflow is `success` or `skipped`
- **THEN** that workflow's success gate succeeds

#### Scenario: A dependency job fails

- **WHEN** any dependency job result in a workflow is `failure` or `cancelled`
- **THEN** that workflow's success gate fails

#### Scenario: A Go job fails

- **WHEN** a Go lint, build, or test job reports `failure`
- **THEN** the enclosing workflow's success gate fails

#### Scenario: Go jobs skipped by path filtering

- **WHEN** a PR changes no gateway files, so the Go jobs are skipped and all
  other jobs succeed
- **THEN** the affected workflow's success gate succeeds

#### Scenario: Some jobs skipped by path filtering

- **WHEN** a PR changes only `app/`, so the backend matrix and other component
  jobs are skipped and the app job succeeds
- **THEN** the affected workflow's success gate succeeds

### Requirement: DevOps hardening

The workflow SHALL apply the following hardening measures: all third-party
actions pinned to a full commit SHA, workflow-level least-privilege permissions
defaulting to read-only, concurrency control that cancels superseded runs on the
same ref, and dependency/build caching for Gradle and the pnpm store.

#### Scenario: Actions are SHA-pinned

- **WHEN** the workflow references any non-GitHub-owned action
- **THEN** the reference uses a full commit SHA (a version tag alone is not
  sufficient)

#### Scenario: Least-privilege permissions

- **WHEN** the workflow runs
- **THEN** the default token permissions are read-only (`contents: read`) and no
  job requests write scope

#### Scenario: Superseded runs are cancelled

- **WHEN** a new commit is pushed to a ref that already has an in-progress CI
  run
- **THEN** the in-progress run for that ref is cancelled and the new run starts

#### Scenario: Caches are reused across runs

- **WHEN** a job runs after a previous run populated the Gradle and pnpm caches
- **THEN** the job restores those caches instead of downloading everything anew

### Requirement: Go validation jobs

For changes selecting the `gateway` output, the lint workflow SHALL verify Go
formatting and run the configured Go linters, the build workflow SHALL compile
the module, and the test workflow SHALL execute the module's test suite. A
formatting deviation, a lint finding, a compilation error, or a test failure
SHALL fail the corresponding job. The lint job SHALL check formatting without
modifying tracked files.

#### Scenario: Gateway passes all Go checks

- **WHEN** the gateway is correctly formatted, produces no lint findings,
  compiles, and all its tests pass
- **THEN** the Go lint, build, and test jobs all succeed

#### Scenario: Unformatted Go file fails lint

- **WHEN** any tracked Go file's formatting differs from the canonical format
- **THEN** the Go lint job fails and identifies the offending file

#### Scenario: Lint finding fails the job

- **WHEN** a configured linter reports a finding, such as an unchecked error
  return value
- **THEN** the Go lint job fails and reports the finding location

#### Scenario: Lint job does not rewrite files

- **WHEN** the Go lint job runs against an unformatted file
- **THEN** it reports the deviation without writing changes to the working tree

#### Scenario: Compilation error fails the build

- **WHEN** the gateway module fails to compile
- **THEN** the Go build job fails

#### Scenario: Failing Go test fails the test job

- **WHEN** any test in the gateway module fails
- **THEN** the Go test job fails

#### Scenario: Go jobs skipped when the gateway is untouched

- **WHEN** a PR changes no files under `services/gateway/`
- **THEN** the Go lint, build, and test jobs are skipped

### Requirement: Build artifacts are not tracked

Compiled binaries produced by building the gateway SHALL NOT be tracked in
version control, and the repository ignore rules SHALL prevent them from being
staged.

#### Scenario: Compiled binary is ignored

- **WHEN** a developer builds the gateway in place, producing a compiled binary
  in the module directory
- **THEN** the binary does not appear as an untracked or staged change

#### Scenario: No compiled binary is tracked

- **WHEN** the tracked file list for `services/gateway/` is inspected
- **THEN** it contains no compiled executable
