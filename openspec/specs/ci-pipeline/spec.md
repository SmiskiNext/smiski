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

The workflow SHALL detect which projects are affected by a change using
`nx affected` driven by `nrwl/nx-set-shas` to compute the base and head SHAs,
rather than classifying changed paths with `dorny/paths-filter`. Task workflows
SHALL run only the affected projects for their task. The security secret scan is
exempt and SHALL always run (see Security scan job).

#### Scenario: Only backend changed

- **WHEN** a change modifies files only under a backend service
- **THEN** `nx affected` selects that service (and its dependents) for the
  build/test/lint tasks, and unaffected projects such as `app` and `scripts` are
  not executed

#### Scenario: Only Forge app changed

- **WHEN** a change modifies files only under `app/`
- **THEN** `nx affected` selects the `app` project and its dependents, and the
  backend services, `scripts`, and `docs` projects are not executed

#### Scenario: Proto changed

- **WHEN** a change modifies files under `services/proto/`
- **THEN** `nx affected` selects the proto project so its buf targets run

#### Scenario: Multiple components changed

- **WHEN** a change modifies files under both a backend service and `app/`
- **THEN** `nx affected` selects both projects and their dependents

#### Scenario: Base SHA resolution on protected-branch push

- **WHEN** a commit is pushed to `dev` or `main`
- **THEN** `nrwl/nx-set-shas` resolves the affected base against the last
  successful run, falling back to a configured base on the first run

### Requirement: Per-tool toolchain provisioning

Every job SHALL provision its toolchain with dedicated setup actions rather than
`jdx/mise-action`: `actions/setup-java` (Temurin, Java 25), `actions/setup-node`
with pnpm store caching plus `pnpm/action-setup`, `bufbuild/buf-action` for Buf,
and a direct gitleaks binary install. Tools that are local-only (`lefthook`,
`mongosh`) SHALL NOT be installed in CI. A shared composite action SHALL
centralize the common setup steps.

#### Scenario: Java and Node toolchains are provisioned per job

- **WHEN** a task workflow needs the JVM and Node toolchains
- **THEN** it installs Java 25 via `actions/setup-java` and Node + pnpm via
  `actions/setup-node` and `pnpm/action-setup`, without invoking `mise`

#### Scenario: Local-only tools are absent in CI

- **WHEN** any CI job provisions its toolchain
- **THEN** neither `lefthook` nor `mongosh` is installed

#### Scenario: Shared setup is reused

- **WHEN** multiple task workflows need the same base setup
- **THEN** they invoke the shared composite setup action instead of duplicating
  setup steps

### Requirement: Task-oriented workflow organization

CI SHALL be organized as task-oriented workflows — `lint`, `test`, `build`,
`security`, and `proto` — instead of a single component-oriented workflow. Each
non-security workflow SHALL execute its task via `nx affected` across the whole
graph, so a task spans backend services and pnpm packages uniformly.

#### Scenario: Lint workflow covers the whole graph

- **WHEN** the `lint` workflow runs
- **THEN** it runs the lint task on all affected projects across Gradle services
  and pnpm packages

#### Scenario: Build workflow preserves backend gates

- **WHEN** the `build` workflow runs for an affected backend service
- **THEN** the JaCoCo coverage gate and the OpenAPI drift check still execute as
  part of that service's build, with unchanged thresholds

#### Scenario: Proto workflow runs buf checks

- **WHEN** the `proto` workflow runs and the proto project is affected
- **THEN** `buf lint` and `buf breaking` (against `dev`) execute

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

The workflow set SHALL provide an aggregate success gate that depends on all
task workflows and the security scan, runs even when some affected task runs are
no-ops, and succeeds only if no dependency failed or was cancelled. Branch
protection is expected to require only this single check.

#### Scenario: All run tasks succeed

- **WHEN** every task workflow and the security scan result is `success` or a
  no-op success
- **THEN** the aggregate success gate succeeds

#### Scenario: A task workflow fails

- **WHEN** any task workflow or the security scan result is `failure` or
  `cancelled`
- **THEN** the aggregate success gate fails

#### Scenario: Some tasks are no-ops by affected scoping

- **WHEN** a change affects only `app`, so the backend build/test tasks select
  no projects while the app tasks and the security scan succeed
- **THEN** the aggregate success gate succeeds

### Requirement: DevOps hardening

The workflow SHALL apply the following hardening measures: all third-party
actions pinned to a full commit SHA with a trailing version comment,
workflow-level least-privilege permissions defaulting to read-only, concurrency
control that cancels superseded runs on the same ref, dependency/build caching
for Gradle and the pnpm store, caching of the Nx cache directory (`.nx`) via
`actions/cache`, and automated SHA-pin maintenance via Renovate.

#### Scenario: Actions are SHA-pinned

- **WHEN** the workflow references any non-GitHub-owned action
- **THEN** the reference uses a full commit SHA (a version tag alone is not
  sufficient) accompanied by a version comment

#### Scenario: Least-privilege permissions

- **WHEN** the workflow runs
- **THEN** the default token permissions are read-only (`contents: read`) and no
  job requests write scope beyond what its task requires

#### Scenario: Superseded runs are cancelled

- **WHEN** a new commit is pushed to a ref that already has an in-progress CI
  run
- **THEN** the in-progress run for that ref is cancelled and the new run starts

#### Scenario: Caches are reused across runs

- **WHEN** a job runs after a previous run populated the Gradle, pnpm, and Nx
  caches
- **THEN** the job restores those caches instead of recomputing everything anew

#### Scenario: SHA pins are kept current automatically

- **WHEN** a pinned action publishes a newer release
- **THEN** Renovate opens a pull request updating the commit SHA and its version
  comment
