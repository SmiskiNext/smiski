# ADDED Requirements

## Requirement: PR lint workflow triggers on pull requests to main

The system SHALL run a `lint` GitHub Actions workflow on every pull request
opened, synchronized, or reopened against the `main` branch. All lint jobs SHALL
run in parallel. The workflow SHALL fail if any individual job fails.

### Scenario: PR opened against main triggers lint workflow

- **WHEN** a pull request is opened targeting the `main` branch
- **THEN** the `lint.yml` workflow is triggered and all parallel jobs start

### Scenario: All lint jobs pass

- **WHEN** all parallel lint jobs complete without errors
- **THEN** the workflow reports success and the PR check passes

### Scenario: One lint job fails

- **WHEN** any single lint job exits with a non-zero code
- **THEN** that job is marked as failed, its log is available, and the overall
  workflow status is failure

## Requirement: Java code format check

The system SHALL run `./services/gradlew spotlessCheck` as the `services-format`
job inside the lint workflow. This job SHALL use JDK 25 and SHALL cache Gradle
artifacts via `gradle/actions/setup-gradle@v4`.

### Scenario: Java source files are correctly formatted

- **WHEN** all Java source files in `services/` comply with Spotless rules
- **THEN** the `services-format` job exits with code 0

### Scenario: Java source file has formatting violation

- **WHEN** a Java file in `services/` has a Spotless violation
- **THEN** the `services-format` job exits with a non-zero code and logs the
  violating file path and diff

## Requirement: Proto format check

The system SHALL run `./services/gradlew -p services/proto bufFormatCheck` as
the `proto-format` job inside the lint workflow.

### Scenario: Proto files are correctly formatted

- **WHEN** all `.proto` files comply with Buf formatting rules
- **THEN** the `proto-format` job exits with code 0

### Scenario: Proto file has formatting violation

- **WHEN** a `.proto` file has a Buf formatting violation
- **THEN** the `proto-format` job exits with a non-zero code and logs the diff

## Requirement: Web code check via Biome

The system SHALL run `pnpm --dir frontends/web biome check` (including lint and
format) as the `web-check` job inside the lint workflow. This job SHALL set up
Node LTS and pnpm via `pnpm/action-setup` before running.

### Scenario: Web source files pass Biome check

- **WHEN** all files under `frontends/web/` satisfy Biome lint and format rules
- **THEN** the `web-check` job exits with code 0

### Scenario: Web source file has Biome violation

- **WHEN** a file under `frontends/web/` has a Biome lint or format error
- **THEN** the `web-check` job exits with a non-zero code and reports the
  affected file and rule

## Requirement: General file format check via Prettier

The system SHALL run Prettier in check mode for
`**/*.{md,json,toml,yaml,yml,sh}` as the `general-format` job inside the lint
workflow. The check SHALL exclude `frontends/web/` and `node_modules/`.

### Scenario: All general files are correctly formatted

- **WHEN** all markdown, JSON, TOML, YAML, and shell files comply with Prettier
  formatting
- **THEN** the `general-format` job exits with code 0

### Scenario: A YAML file has formatting violation

- **WHEN** a `.yml` or `.yaml` file at repo root or in `services/` has a
  Prettier violation
- **THEN** the `general-format` job exits with a non-zero code and reports the
  file path

## Requirement: Commitlint validates all PR commits

The system SHALL validate every commit in the pull request's commit range
against the conventional-commits rules defined in `commitlint.config.js` as the
`commitlint` job inside the lint workflow. The range SHALL be from
`github.event.pull_request.base.sha` (exclusive) to `HEAD` (inclusive).

### Scenario: All PR commits have valid conventional-commit messages

- **WHEN** every commit message in the PR matches the `commitlint.config.js`
  rules (type, scope, subject, signed-off-by)
- **THEN** the `commitlint` job exits with code 0

### Scenario: A commit message is missing a type prefix

- **WHEN** a commit in the PR has a message that does not start with a valid
  conventional-commit type (e.g., `feat:`, `fix:`)
- **THEN** the `commitlint` job exits with a non-zero code and reports the
  offending commit SHA and message
