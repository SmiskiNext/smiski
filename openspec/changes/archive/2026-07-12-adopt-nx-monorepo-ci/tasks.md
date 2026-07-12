## 1. Nx workspace bootstrap

- [x] 1.1 Add `nx` (≥ 20.7) and `@nx/gradle` as root devDependencies in
      `package.json` and install with pnpm
- [x] 1.2 Create root `nx.json` configuring the `@nx/gradle` plugin
      (`gradleExecutableDirectory` targeting `./services`) and namedInputs for
      the docs project
- [x] 1.3 Add `.nx/` to root `.gitignore`
- [x] 1.4 Add the `dev.nx.gradle.project-graph` companion plugin coordinate to
      `gradle/libs.versions.toml`

## 2. Gradle project-graph integration

- [x] 2.1 Apply the `dev.nx.gradle.project-graph` plugin and register a
      `projectReportAll` task (fanning out to `gradle.includedBuilds`) in the
      root `build.gradle.kts`
- [x] 2.2 Apply the plugin and add `projectReportAll` bridging in
      `services/build.gradle.kts` and `build-logic`
- [x] 2.3 Apply the plugin and add `projectReportAll` bridging in each service
      build file: `proto`, `shared`, `tenant`, `meet`, `record`, `notification`

## 3. pnpm and docs projects

- [x] 3.1 Add `app/project.json` with targets `lint` (biome), `typecheck`
      (`tsc --noEmit`), `build-static` (nested `static/hello-world`), and
      `forge-lint`
- [x] 3.2 Add `scripts/project.json` with targets `lint` (biome) and `typecheck`
- [x] 3.3 Add a root `docs` Nx project with a `lint` target (markdownlint +
      Prettier check) scoped by namedInputs to `md/json/toml/yaml/yml/sh`
- [x] 3.4 Add `buf-lint` and `buf-breaking` (against `dev`) targets to the
      `services/proto` project

## 4. Graph validation gate (D2)

- [x] 4.1 Run `nx show projects` and confirm `proto`, `shared`, `tenant`,
      `meet`, `record`, `notification`, `app`, `scripts`, `docs` all appear
- [x] 4.2 If any Gradle build is missing, correct its `projectReportAll`
      bridging and re-verify before proceeding to CI

## 5. Shared CI setup action

- [x] 5.1 Create `.github/actions/setup/action.yml` (composite) that checks out
      with full history, sets up Java 25 (`actions/setup-java`, Temurin), Node +
      pnpm (`actions/setup-node` + `pnpm/action-setup`, pnpm store cache), and
      restores the `.nx` cache via `actions/cache`
- [x] 5.2 Pin every action in the composite to a full commit SHA with a `# vX.Y`
      version comment

## 6. Task-oriented workflows

- [x] 6.1 Create `.github/workflows/lint.yml` running `nx affected -t lint` via
      the shared setup + `nrwl/nx-set-shas`
- [x] 6.2 Create `.github/workflows/test.yml` running `nx affected -t test`
- [x] 6.3 Create `.github/workflows/build.yml` running `nx affected -t build`,
      preserving the JaCoCo coverage gate, JaCoCo report artifact upload, and
      OpenAPI drift check for affected backend services
- [x] 6.4 Create `.github/workflows/proto.yml` running the proto `buf-lint` and
      `buf-breaking` targets via `bufbuild/buf-action`
- [x] 6.5 Create `.github/workflows/security.yml` that installs the gitleaks
      binary directly and always runs a secret scan (PR:
      `--log-opts BASE..HEAD`; otherwise full scan), independent of
      `nx affected`
- [x] 6.6 Add an aggregate success gate that depends on all task workflows and
      the security scan and succeeds only if none failed or was cancelled
- [x] 6.7 Apply workflow-level `permissions: contents: read` and per-ref
      `concurrency` with `cancel-in-progress` across all workflows
- [x] 6.8 Pin every third-party action across all workflows to a full commit SHA
      with a version comment

## 7. Renovate + cleanup

- [x] 7.1 Add `renovate.json` extending `helpers:pinGitHubActionDigests` and
      `helpers:pinGitHubActionDigestsToSemver` with a scheduled update cadence
- [x] 7.2 Delete `.github/workflows/ci.yml`
- [x] 7.3 Update `AGENTS.md` command docs to reference the Nx-based CI and
      confirm `.mise.toml` remains local-only (no CI usage)

## 8. Verification (from spec scenarios)

- [x] 8.1 `nx affected -t build` with a single-service change builds only that
      service and its dependents (nx-orchestration: affected scoping)
- [x] 8.2 Re-running a task with identical inputs restores from cache instead of
      re-executing (nx-orchestration: cached task)
- [x] 8.3 A change outside every project's inputs makes `nx affected` a no-op
      success (nx-orchestration: no projects affected)
- [x] 8.4 `nx show projects` lists all six Gradle builds plus app/scripts/docs
      (nx-orchestration: Gradle + pnpm graph)
- [x] 8.5 Run each workflow locally with `act` (using `.actrc`) and confirm no
      `mise`, `lefthook`, or `mongosh` is invoked (ci-pipeline: per-tool
      provisioning)
- [x] 8.6 A docs-only change still triggers the security scan (ci-pipeline:
      security scan is affected-independent)
- [x] 8.7 A backend build below coverage thresholds fails the build workflow and
      still uploads the JaCoCo report (ci-pipeline: coverage gate)
- [x] 8.8 A stale committed `openapi.yaml` fails the build workflow drift check
      (ci-pipeline: OpenAPI drift)
- [x] 8.9 A `buf breaking` change against `dev` fails the proto workflow
      (ci-pipeline: proto job)
- [x] 8.10 Confirm every third-party action reference is a full commit SHA with
      a version comment and the aggregate gate is the single required check
      (ci-pipeline: DevOps hardening + aggregate success gate)
