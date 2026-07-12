## MODIFIED Requirements

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Toolchain provisioning from `.mise.toml`

**Reason**: CI no longer installs a version manager; each tool is provisioned by
a dedicated setup action (see the new "Per-tool toolchain provisioning"
requirement). `.mise.toml` is retained for local development only.

**Migration**: Replace `jdx/mise-action` steps with `actions/setup-java`,
`actions/setup-node` + `pnpm/action-setup`, `bufbuild/buf-action`, and a direct
gitleaks binary install. Tool versions now live in the composite setup action;
`lefthook` and `mongosh` are dropped from CI.
