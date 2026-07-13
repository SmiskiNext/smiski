## MODIFIED Requirements

### Requirement: Change detection and path filtering

Change detection SHALL be provided by a reusable composite action at
`.github/actions/detect-changes` that every validation workflow invokes as a
step. The action SHALL classify changed paths and expose: a `services` output
containing a JSON array of the changed backend service names for use as a job
matrix, boolean outputs `app`, `scripts`, `proto`, and `docs`, and an
`openapi_services` output listing the subset of changed services that emit an
OpenAPI spec (`tenant`, `meet`, `record`). Each service entry SHALL be selected
when its own directory changes OR when any shared input changes:
`services/shared/**`, `services/proto/**`, `build-logic/**`, or
`gradle/libs.versions.toml`. Each component job SHALL run only when its
corresponding output is truthy or its matrix is non-empty.

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

#### Scenario: Only docs changed

- **WHEN** a PR modifies only Markdown or root config files
- **THEN** the `docs` output is `true`, the `services` output is an empty array,
  and the backend matrix jobs are skipped

#### Scenario: OpenAPI subset excludes notification

- **WHEN** a PR changes `services/notification/` only
- **THEN** `notification` appears in `services` but NOT in `openapi_services`

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

#### Scenario: Coverage meets thresholds

- **WHEN** measured line coverage ≥ 70% and branch coverage ≥ 60% for a service
- **THEN** the coverage gate passes for that service

#### Scenario: Coverage below thresholds

- **WHEN** measured coverage for a service is below the configured thresholds
- **THEN** the coverage gate fails the test job for that service

#### Scenario: Coverage report is retained

- **WHEN** the test job completes, whether the coverage gate passed or failed
- **THEN** the JaCoCo HTML/XML report is uploaded as a workflow artifact

### Requirement: OpenAPI drift check

The test workflow SHALL, for each changed service in `openapi_services`,
regenerate the per-service OpenAPI spec, fail if the committed
`services/<name>/openapi.yaml` differs from the regenerated output, and lint the
specs with Redocly.

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
depends on the workflow's jobs, runs even when some dependencies are skipped,
and succeeds only if no dependency failed or was cancelled. Branch protection is
expected to require these four per-workflow checks.

#### Scenario: All run jobs succeed

- **WHEN** every dependency job result in a workflow is `success` or `skipped`
- **THEN** that workflow's success gate succeeds

#### Scenario: A dependency job fails

- **WHEN** any dependency job result in a workflow is `failure` or `cancelled`
- **THEN** that workflow's success gate fails

#### Scenario: Some jobs skipped by path filtering

- **WHEN** a PR changes only `app/`, so the backend matrix and other component
  jobs are skipped and the app job succeeds
- **THEN** the affected workflow's success gate succeeds
