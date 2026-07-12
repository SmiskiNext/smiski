# ci-pipeline Specification

## Purpose

TBD - created by archiving change ci-workflow. Update Purpose after archive.

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

The workflow SHALL include a `changes` job that classifies the changed paths
into the components `services`, `app`, `scripts`, `proto`, and `docs`, and
exposes each as a boolean output. Each component validation job SHALL run only
when its corresponding output is `true`.

#### Scenario: Only backend changed

- **WHEN** a PR modifies files only under `services/` (excluding
  `services/proto`)
- **THEN** the backend job runs and the app, scripts, proto, and docs jobs are
  skipped

#### Scenario: Only Forge app changed

- **WHEN** a PR modifies files only under `app/`
- **THEN** the forge-app job runs and the backend, scripts, proto, and docs jobs
  are skipped

#### Scenario: Proto changed

- **WHEN** a PR modifies files under `services/proto/`
- **THEN** the proto job runs

#### Scenario: Multiple components changed

- **WHEN** a PR modifies files under both `services/` and `app/`
- **THEN** both the backend and forge-app jobs run

### Requirement: Toolchain provisioning from `.mise.toml`

Every job that needs project tooling SHALL install it via `jdx/mise-action`
reading the repository `.mise.toml`, rather than hardcoding tool versions in the
workflow. The workflow SHALL NOT duplicate the Java, Node, pnpm, Buf, or
gitleaks version numbers.

#### Scenario: Tool versions match local configuration

- **WHEN** any CI job provisions its toolchain
- **THEN** the installed Java, Node, pnpm, Buf, and gitleaks versions are those
  resolved from `.mise.toml`

#### Scenario: Adding a tool pin flows to CI without workflow edits

- **WHEN** a new tool is added to `.mise.toml`
- **THEN** CI jobs can use it without editing version numbers in the workflow

### Requirement: Backend validation job

For changes touching `services/`, the workflow SHALL run the Gradle composite
build (`./services/gradlew build`) covering the fast `test` source set and the
container-backed `integrationTest` source set on a Docker-enabled runner. The
job SHALL fail if any test fails.

#### Scenario: Backend tests pass

- **WHEN** the backend job runs `./services/gradlew build` and all unit,
  ArchUnit, and integration tests pass
- **THEN** the backend job succeeds

#### Scenario: A backend test fails

- **WHEN** any unit, ArchUnit, or integration test fails during the build
- **THEN** the backend job fails and reports failure on the check

#### Scenario: Testcontainers integration tests run without extra infra

- **WHEN** the `integrationTest` source set executes on the runner
- **THEN** Testcontainers provisions its own containers using the runner's
  preinstalled Docker, with no manually declared service containers

### Requirement: Coverage gate

The backend job SHALL enforce the JaCoCo coverage gate
(`jacocoTestCoverageVerification`, line ≥ 70% / branch ≥ 60%) and publish the
JaCoCo report as a build artifact. The CI-enforced thresholds SHALL match those
defined in the Gradle convention plugin and SHALL NOT be lowered by the
workflow.

#### Scenario: Coverage meets thresholds

- **WHEN** measured line coverage ≥ 70% and branch coverage ≥ 60%
- **THEN** the coverage gate passes

#### Scenario: Coverage below thresholds

- **WHEN** measured coverage is below the configured thresholds
- **THEN** the coverage gate fails the backend job

#### Scenario: Coverage report is retained

- **WHEN** the backend job completes, whether the coverage gate passed or failed
- **THEN** the JaCoCo HTML/XML report is uploaded as a workflow artifact

### Requirement: OpenAPI drift check

For changes touching `services/`, the workflow SHALL regenerate the per-service
OpenAPI specs, fail if any committed `services/**/openapi.yaml` differs from the
regenerated output, and lint the specs with Redocly.

#### Scenario: Committed specs match generated output

- **WHEN** regenerating the OpenAPI specs produces no change to the committed
  `openapi.yaml` files and Redocly lint passes
- **THEN** the OpenAPI drift check passes

#### Scenario: Committed specs are stale

- **WHEN** regenerating the OpenAPI specs produces a diff against the committed
  `openapi.yaml` files
- **THEN** the check fails with a message instructing the author to run
  `pnpm run openapi` locally and commit the result

#### Scenario: Spec violates lint rules

- **WHEN** Redocly lint reports an error against a generated spec
- **THEN** the OpenAPI drift check fails

### Requirement: Forge app validation job

For changes touching `app/`, the workflow SHALL run Biome check and
`tsc --noEmit` on the app root, build the nested `static/hello-world` UI, and
run `forge lint` against `manifest.yml`. The job SHALL NOT deploy or install the
Forge app.

#### Scenario: App passes all checks

- **WHEN** Biome, `tsc`, the `static/hello-world` build, and `forge lint` all
  succeed
- **THEN** the forge-app job succeeds

#### Scenario: UI build fails

- **WHEN** the `static/hello-world` react-scripts build fails
- **THEN** the forge-app job fails

#### Scenario: Manifest is invalid

- **WHEN** `forge lint` reports an error against `manifest.yml`
- **THEN** the forge-app job fails

#### Scenario: No deployment occurs

- **WHEN** the forge-app job runs
- **THEN** no `forge deploy` or `forge install` command is executed and no Forge
  credentials are required

### Requirement: Scripts validation job

For changes touching `scripts/`, the workflow SHALL run Biome check and
`tsc --noEmit` for the CLI package.

#### Scenario: Scripts pass lint and typecheck

- **WHEN** Biome and `tsc --noEmit` both succeed for `scripts/`
- **THEN** the scripts job succeeds

#### Scenario: Type error in scripts

- **WHEN** `tsc --noEmit` reports a type error in `scripts/`
- **THEN** the scripts job fails

### Requirement: Proto validation job

For changes touching `services/proto/`, the workflow SHALL run `buf lint` and
`buf breaking` comparing against the `dev` branch.

#### Scenario: Proto lint and breaking pass

- **WHEN** `buf lint` reports no issues and `buf breaking` finds no breaking
  changes against `dev`
- **THEN** the proto job succeeds

#### Scenario: Breaking change detected

- **WHEN** `buf breaking` detects a breaking change against `dev`
- **THEN** the proto job fails

### Requirement: Security scan job

The workflow SHALL always run a `gitleaks detect` full-history secret scan
regardless of which components changed, using the repository `.gitleaks.toml`
configuration.

#### Scenario: No secrets present

- **WHEN** the gitleaks scan finds no secrets
- **THEN** the security job succeeds

#### Scenario: Secret detected

- **WHEN** gitleaks detects a secret in the repository history
- **THEN** the security job fails

#### Scenario: Security scan is component-independent

- **WHEN** a PR changes only documentation
- **THEN** the security job still runs

### Requirement: Docs validation job

For changes touching documentation and root config files, the workflow SHALL run
markdownlint on Markdown files and Prettier in check mode on
`md/json/toml/yaml/yml/sh` files.

#### Scenario: Docs are well-formatted

- **WHEN** markdownlint and Prettier `--check` both pass
- **THEN** the docs job succeeds

#### Scenario: Formatting violation

- **WHEN** Prettier `--check` reports an unformatted file
- **THEN** the docs job fails

### Requirement: Aggregate success gate

The workflow SHALL include a `ci-success` job that depends on all component
validation jobs and the security job, runs even when some dependencies are
skipped, and succeeds only if no dependency failed or was cancelled. Branch
protection is expected to require only this single check.

#### Scenario: All run jobs succeed

- **WHEN** every dependency job result is `success` or `skipped`
- **THEN** the `ci-success` job succeeds

#### Scenario: A dependency job fails

- **WHEN** any dependency job result is `failure` or `cancelled`
- **THEN** the `ci-success` job fails

#### Scenario: Some jobs skipped by path filtering

- **WHEN** a PR changes only `app/`, so backend, scripts, proto, and docs jobs
  are skipped and the forge-app and security jobs succeed
- **THEN** the `ci-success` job succeeds

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
