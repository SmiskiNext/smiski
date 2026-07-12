## Why

The monorepo has no unified task orchestration: the JVM side (nested Gradle
composite builds) and the pnpm side (`app/`, `scripts/`) are validated by
separate CI jobs that rebuild everything and rely on hand-written
`dorny/paths-filter` globs for change detection. CI also provisions its
toolchain through `jdx/mise-action`, coupling every runner to a heavyweight
version manager, and references third-party actions by mutable tags. Adopting Nx
as an orchestration layer gives one dependency graph across Java and TypeScript,
`affected`-scoped runs with caching, and lets us harden CI provisioning and
supply chain at the same time.

## What Changes

- Introduce Nx as the monorepo orchestrator: root `nx.json`, `nx` as a root
  devDependency, and `.nx/` cache ignored by git.
- Integrate `@nx/gradle` with full project-graph discovery across the nested
  composite builds via the `dev.nx.gradle.project-graph` companion plugin and a
  bridging `projectReportAll` task in every Gradle build file (root, `services`,
  `build-logic`, and each service).
- Model the pnpm side as Nx projects: `app`, `scripts`, a root `docs` project
  (markdownlint + Prettier check), and `buf-lint`/`buf-breaking` targets on
  `services/proto`.
- **BREAKING**: Replace `dorny/paths-filter` change detection with `nx affected`
  driven by `nrwl/nx-set-shas`.
- **BREAKING**: Remove `jdx/mise-action` from CI; provision each tool with a
  dedicated action (`actions/setup-java` for Java 25, `actions/setup-node` +
  `pnpm/action-setup`, `bufbuild/buf-action`) and install the gitleaks binary
  directly. `.mise.toml` stays for local development only.
- **BREAKING**: Split the single `ci.yml` into task-oriented workflows — `lint`,
  `test`, `build`, `security`, `proto` — sharing a composite setup action.
- Keep the gitleaks secret scan always-on, independent of `nx affected`.
- Pin every third-party action to a full commit SHA with a version comment, and
  add Renovate (`helpers:pinGitHubActionDigests`) to keep the pins current.

## Capabilities

### New Capabilities

- `nx-orchestration`: Nx-based project graph across the Gradle composite builds
  and pnpm packages, `affected`-scoped task execution, task caching, and the
  target model that exposes Gradle, Biome, tsc, buf, and docs work as Nx
  targets.

### Modified Capabilities

- `ci-pipeline`: Change detection moves from path filtering to `nx affected`;
  toolchain provisioning moves from `.mise.toml`/`mise-action` to per-tool setup
  actions; the single component-oriented workflow is split into task-oriented
  workflows; SHA pinning is enforced and automated with Renovate.

## Impact

- **New files**: `nx.json`, `app/project.json`, `scripts/project.json`, a root
  docs project config, `renovate.json`, `.github/actions/setup/action.yml`,
  workflows `.github/workflows/{lint,test,build,security,proto}.yml`.
- **Modified**: every Gradle build file (adds companion plugin +
  `projectReportAll`), root `package.json` (Nx deps + scripts), `.gitignore`
  (`.nx/`), `gradle/libs.versions.toml` (companion plugin coordinate).
- **Removed**: `.github/workflows/ci.yml`.
- **Dependencies**: adds `nx` and `@nx/gradle` (Node), the
  `dev.nx.gradle.project-graph` Gradle plugin, and the Renovate GitHub app.
- **Unchanged**: `zms/` legacy tree, Gradle build/test/coverage/mutation logic
  and thresholds, `.mise.toml` for local use, the composite build topology.
