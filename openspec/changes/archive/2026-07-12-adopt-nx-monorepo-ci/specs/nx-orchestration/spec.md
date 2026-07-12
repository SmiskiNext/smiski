## ADDED Requirements

### Requirement: Nx workspace configuration

The repository SHALL define an Nx workspace at the root via `nx.json`, declare
`nx` as a root devDependency, and exclude the Nx cache directory (`.nx/`) from
version control. Nx SHALL be version 20.7 or later.

#### Scenario: Nx workspace is recognized

- **WHEN** `nx` commands are run from the repository root
- **THEN** Nx resolves the workspace configuration and lists the workspace
  projects without error

#### Scenario: Cache directory is not committed

- **WHEN** Nx writes task results to `.nx/`
- **THEN** those files are ignored by git and never appear in a commit

#### Scenario: Unsupported Nx version rejected

- **WHEN** the installed Nx version is below 20.7
- **THEN** the setup is treated as unsupported because nested composite build
  graph discovery is not reliable below that version

### Requirement: Gradle project graph integration

The Nx workspace SHALL discover every Gradle build in the nested composite build
through the `@nx/gradle` plugin and the `dev.nx.gradle.project-graph` companion
plugin. Each Gradle build file (root, `services`, `build-logic`, and every
service) SHALL expose a `projectReportAll` task that fans out to its own
`gradle.includedBuilds`, so graph traversal crosses both composite levels.

#### Scenario: All Gradle builds appear in the graph

- **WHEN** `nx show projects` is run
- **THEN** the output includes `proto`, `shared`, `tenant`, `meet`, `record`,
  and `notification`

#### Scenario: Gradle tasks are exposed as Nx targets

- **WHEN** a service is inspected through Nx
- **THEN** its Gradle lifecycle tasks (such as `build` and `test`) are available
  as Nx targets for that project

#### Scenario: A Gradle build fails to appear

- **WHEN** `nx show projects` omits one of the expected Gradle builds
- **THEN** the integration is considered incomplete and the missing build's
  `projectReportAll` bridging MUST be corrected before CI relies on
  `nx affected`

### Requirement: pnpm and docs projects in the graph

The Nx workspace SHALL model the pnpm-side packages and repository docs as Nx
projects: `app`, `scripts`, a root `docs` project (markdownlint + Prettier
check), and `buf-lint`/`buf-breaking` targets on `services/proto`.

#### Scenario: pnpm packages are Nx projects

- **WHEN** `nx show projects` is run
- **THEN** the output includes `app` and `scripts`

#### Scenario: Docs validation is an Nx target

- **WHEN** the `docs` project's lint target runs
- **THEN** it executes markdownlint and Prettier check over the repository
  Markdown and config files

#### Scenario: Proto buf checks are Nx targets

- **WHEN** the `services/proto` project is inspected
- **THEN** `buf-lint` and `buf-breaking` are available as Nx targets

### Requirement: Affected-scoped task execution

The workspace SHALL support running `nx affected -t <task>` so that only
projects affected by a change are built, tested, or linted, using task caching
to skip unchanged work.

#### Scenario: Only affected projects run

- **WHEN** a change modifies exactly one service and `nx affected -t build` runs
- **THEN** only that service (and its dependents) build, and unaffected projects
  are skipped

#### Scenario: Cached task is not re-executed

- **WHEN** a task runs against inputs identical to a previously cached run
- **THEN** Nx restores the cached result instead of re-executing the task

#### Scenario: No projects affected

- **WHEN** a change touches only files outside every project's inputs
- **THEN** `nx affected` selects no projects and the task run completes as a
  no-op success
