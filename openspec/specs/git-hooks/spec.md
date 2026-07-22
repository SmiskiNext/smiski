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
`services/proto/**/*.proto`, Prettier `--write` for
`**/*.{md,json,toml,yaml,yml,sh,sql}`, markdownlint `--fix` for `**/*.md`, and
Biome `check --write` for `scripts/**` and `app/src/**`. Fixed files SHALL be
re-staged.

#### Scenario: Staged Java file is formatted

- **WHEN** a commit stages a `services/**/*.java` file
- **THEN** Spotless reformats it and the reformatted content is re-staged

#### Scenario: Staged SQL file is formatted

- **WHEN** a commit stages a `.sql` file
- **THEN** Prettier formats it via `prettier-plugin-sql` and re-stages it

#### Scenario: Pre-commit does not lint or typecheck

- **WHEN** the pre-commit hook runs
- **THEN** no non-fixing lint (e.g. `biome lint`) and no `tsc --noEmit` or
  `compileJava` typecheck runs at commit time

### Requirement: Pre-push typecheck and build

The pre-push hook SHALL run typecheck and build for changed packages/directories
and SHALL NOT run non-fixing lint or the heavy react-scripts build. It SHALL
run: `tsc --noEmit` for `scripts` and `app`, and Java `assemble` per changed
service (compilation, which also serves as the Java typecheck). It SHALL block
direct pushes to `main`.

#### Scenario: TypeScript typecheck on push

- **WHEN** a push includes changes under `scripts/` or `app/`
- **THEN** `tsc --noEmit` runs for the affected package and blocks the push on a
  type error

#### Scenario: Java service is assembled

- **WHEN** a push includes changes under `services/<name>/`
- **THEN** `./services/gradlew -p services/<name> assemble` runs and blocks the
  push on a compilation failure

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
emits an OpenAPI spec (`tenant`, `meet`, `record`), regenerate the combined
`services/openapi.yaml` by joining the per-service specs and block the push if
the committed combined document differs from the regenerated output. The hook
SHALL restore the working tree to the committed state after checking, so the
drift check does not leave uncommitted changes.

#### Scenario: Combined spec is up to date

- **WHEN** a push includes changes under `services/{tenant,meet,record}/` and
  the regenerated combined document matches the committed
  `services/openapi.yaml`
- **THEN** the pre-push hook passes the combined drift check

#### Scenario: Combined spec drift blocks the push

- **WHEN** a push includes changes under `services/{tenant,meet,record}/` and
  the regenerated combined document differs from the committed
  `services/openapi.yaml`
- **THEN** the pre-push hook fails with a message instructing the author to run
  `pnpm run openapi` and commit the regenerated combined document

#### Scenario: No relevant service changes skip the check

- **WHEN** a push includes no changes under `services/{tenant,meet,record}/`
- **THEN** the combined drift check is skipped and does not block the push
