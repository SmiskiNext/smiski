## MODIFIED Requirements

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

## ADDED Requirements

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
