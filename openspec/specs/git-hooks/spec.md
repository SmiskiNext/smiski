# git-hooks Specification

## Purpose

Defines the lefthook-managed Git hooks that enforce code quality and safety at
commit and push time. Pre-commit auto-fixes and re-stages staged files. Pre-push
runs typecheck and build for changed packages and blocks direct pushes to
`main`. Secret scanning runs at both stages using gitleaks.

## Requirements

### Requirement: Pre-commit auto-fix and format

The pre-commit hook SHALL only auto-fix and format staged files; it SHALL NOT
run non-fixing lint or typecheck steps. It SHALL run, scoped to staged files:
Spotless (`spotlessApply`) for `services/**/*.{java,kts,xml}`, `buf format` for
`services/proto/**/*.proto`, `gofmt -w` followed by `golangci-lint run --fix`
for `services/gateway/**/*.go`, Prettier `--write` for
`**/*.{md,json,toml,yaml,yml,sh,sql}`, markdownlint `--fix` for `**/*.md`, and
Biome `check --write` for `scripts/**` and `app/src/**`. Fixed files SHALL be
re-staged.

#### Scenario: Staged Java file is formatted

- **WHEN** a commit stages a `services/**/*.java` file
- **THEN** Spotless reformats it and the reformatted content is re-staged

#### Scenario: Staged SQL file is formatted

- **WHEN** a commit stages a `.sql` file
- **THEN** Prettier formats it via `prettier-plugin-sql` and re-stages it

#### Scenario: Staged Go file is formatted

- **WHEN** a commit stages a `services/gateway/**/*.go` file whose formatting
  differs from `gofmt` output
- **THEN** the file is reformatted and the reformatted content is re-staged

#### Scenario: Auto-fixable Go lint finding is corrected

- **WHEN** a commit stages a Go file containing a lint finding that the
  configured linters can fix automatically
- **THEN** the fix is applied and the corrected content is re-staged

#### Scenario: Non-fixable Go lint finding does not block the commit

- **WHEN** a commit stages a Go file containing a lint finding that cannot be
  fixed automatically
- **THEN** the commit is not blocked, because verification is deferred to
  pre-push

#### Scenario: Commit touching no Go files skips Go formatting

- **WHEN** a commit stages no files under `services/gateway/`
- **THEN** no Go formatting or lint step runs

#### Scenario: Pre-commit does not lint or typecheck

- **WHEN** the pre-commit hook runs
- **THEN** no non-fixing lint (e.g. `biome lint`) and no `tsc --noEmit` or
  `compileJava` typecheck runs at commit time

### Requirement: Pre-push typecheck and build

The pre-push hook SHALL run typecheck and build for changed packages/directories
and SHALL NOT run non-fixing lint or the heavy react-scripts build. It SHALL
run: `tsc --noEmit` for `scripts` and `app`, Java `assemble` per changed service
(compilation, which also serves as the Java typecheck), and, when a push
includes changes under `services/gateway/`, `go build ./...` followed by
`go test ./...` for the gateway module. It SHALL block direct pushes to `main`.

#### Scenario: TypeScript typecheck on push

- **WHEN** a push includes changes under `scripts/` or `app/`
- **THEN** `tsc --noEmit` runs for the affected package and blocks the push on a
  type error

#### Scenario: Java service is assembled

- **WHEN** a push includes changes under `services/<name>/`
- **THEN** `./services/gradlew -p services/<name> assemble` runs and blocks the
  push on a compilation failure

#### Scenario: Go module is built and tested

- **WHEN** a push includes changes under `services/gateway/`
- **THEN** `go build ./...` and `go test ./...` run for the gateway module and
  the push proceeds only when both succeed

#### Scenario: Go compilation failure blocks the push

- **WHEN** a push includes gateway changes that fail to compile
- **THEN** the pre-push hook fails and rejects the push without running tests

#### Scenario: Failing Go test blocks the push

- **WHEN** a push includes gateway changes that compile but whose test suite has
  a failing test
- **THEN** the pre-push hook fails and rejects the push

#### Scenario: Push touching no gateway files skips Go verification

- **WHEN** a push includes no changes under `services/gateway/`
- **THEN** neither `go build` nor `go test` runs

#### Scenario: React build is not run at push

- **WHEN** a push includes changes under `app/`
- **THEN** the pre-push hook does NOT run `npm run build` for
  `static/hello-world`

#### Scenario: Direct push to main is blocked

- **WHEN** a push targets the `main` branch directly
- **THEN** the pre-push hook fails and rejects the push

### Requirement: Secret scanning across all stages

Secret scanning SHALL run at three stages: `gitleaks protect --staged` at
pre-commit, `gitleaks detect` (full scan) at pre-push, and a `gitleaks detect`
scan in CI via the standalone security workflow. All stages SHALL use the
repository `.gitleaks.toml` configuration.

#### Scenario: Staged secret blocked at commit

- **WHEN** a staged change introduces a secret
- **THEN** `gitleaks protect --staged` fails the commit

#### Scenario: Secret blocked at push

- **WHEN** a push contains a secret in its history
- **THEN** `gitleaks detect` fails the push

#### Scenario: Secret caught in CI

- **WHEN** a commit reaches CI with a secret present
- **THEN** the security workflow's `gitleaks detect` fails the check

### Requirement: Commit message linting

The commit-msg hook SHALL validate the commit message with commitlint using the
repository configuration.

#### Scenario: Non-conventional commit message rejected

- **WHEN** a commit message does not follow the conventional-commit format
- **THEN** the commit-msg hook fails and the commit is rejected

### Requirement: SQL formatting support

Prettier SHALL format `.sql` files using `prettier-plugin-sql`, registered in
`.prettierrc.json` and installed as a root dev dependency. Formatting SHALL be
whitespace-level only and SHALL NOT alter SQL statement semantics.

#### Scenario: Plugin registered

- **WHEN** Prettier runs against a `.sql` file
- **THEN** `prettier-plugin-sql` is loaded from `.prettierrc.json` plugins and
  formats the file

#### Scenario: Migration semantics preserved

- **WHEN** Prettier formats a Flyway baseline migration
- **THEN** only whitespace/layout changes are applied and the SQL statements
  remain semantically identical

### Requirement: Combined OpenAPI drift check at pre-push

The pre-push hook SHALL, when a push includes changes under any service that
emits an OpenAPI spec (`tenant`, `meet`, `record`, `notification`), regenerate
the combined `services/openapi.yaml` by joining the per-service specs and block
the push if the committed combined document differs from the regenerated output.
The hook SHALL restore the working tree to the committed state after checking,
so the drift check does not leave uncommitted changes.

#### Scenario: Combined spec is up to date

- **WHEN** a push includes changes under
  `services/{tenant,meet,record,notification}/` and the regenerated combined
  document matches the committed `services/openapi.yaml`
- **THEN** the pre-push hook passes the combined drift check

#### Scenario: Combined spec drift blocks the push

- **WHEN** a push includes changes under
  `services/{tenant,meet,record,notification}/` and the regenerated combined
  document differs from the committed `services/openapi.yaml`
- **THEN** the pre-push hook fails with a message instructing the author to run
  `pnpm run openapi` and commit the regenerated combined document

#### Scenario: No relevant service changes skip the check

- **WHEN** a push includes no changes under
  `services/{tenant,meet,record,notification}/`
- **THEN** the combined drift check is skipped and does not block the push

### Requirement: Go toolchain provisioning for hooks

The Go toolchain and Go lint tooling used by the hooks SHALL be resolved from
the repository `.mise.toml` rather than from an ambient system installation. The
Go version pinned there SHALL match the version required by the gateway
`go.mod`. Go lint tooling SHALL be configured by a repository-level
configuration file whose schema version matches the pinned tool major version.

#### Scenario: Hook uses the pinned Go version

- **WHEN** a Go hook step runs on a machine where mise has provisioned the
  repository toolchain
- **THEN** the Go version used is the one pinned in `.mise.toml`

#### Scenario: Pinned Go version matches the module requirement

- **WHEN** the pinned Go version in `.mise.toml` is compared with the `go`
  directive in the gateway `go.mod`
- **THEN** the two agree, so no implicit toolchain download is triggered

#### Scenario: Lint configuration is valid for the pinned tool version

- **WHEN** the Go lint configuration file is validated against the pinned lint
  tool
- **THEN** validation succeeds, and a schema mismatch is reported as an explicit
  configuration error rather than as missing lint findings

#### Scenario: Lint tooling is unavailable

- **WHEN** a Go hook step runs on a machine where the Go lint tool is not
  installed
- **THEN** the hook reports the missing tool with installation guidance rather
  than silently passing
