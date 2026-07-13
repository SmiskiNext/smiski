## Why

The single `ci.yml` bundles linting, building, and testing into monolithic
per-component jobs, so any backend change reruns the full Gradle composite build
(compile + unit + integration + coverage) even when only a fast lint or compile
signal is needed, and a change to one microservice rebuilds all of them.
Splitting concerns into dedicated lint / build / test / security workflows with
per-service change detection gives faster, more granular feedback. In parallel,
the git hooks need a clear split: commit-time should only auto-fix and format,
while push-time should typecheck and build, and secret scanning must run at
commit, push, and CI.

## What Changes

- Replace the single `ci.yml` with four independent workflows: `lint.yml`,
  `build.yml`, `test.yml`, and `security.yml`, each with its own aggregate
  success gate.
- Introduce a reusable `.github/actions/detect-changes` composite action that
  classifies changed paths and emits a per-service JSON matrix plus component
  booleans (`app`, `scripts`, `proto`, `docs`), applying fan-out so changes to
  `services/shared`, `services/proto`, `build-logic/`, or
  `gradle/libs.versions.toml` select all services.
- Run Java build and test as a per-service matrix: `build` runs `assemble`
  (compile only, no tests); `test` runs `test` + `integrationTest` + JaCoCo
  coverage gate.
- Route TypeScript `tsc --noEmit` (app, scripts) into the build workflow and the
  OpenAPI drift + Redocly lint into the test workflow.
- Run `gitleaks` as an always-run standalone `security.yml` workflow,
  independent of change detection.
- Rework `lefthook.yml`: **pre-commit** runs gitleaks (staged) plus auto-fix and
  format only; **pre-push** runs gitleaks (full), TypeScript typecheck (app,
  scripts), and Java `assemble` per changed service — no lint, no react build.
- Add `prettier-plugin-sql` so Prettier formats `.sql` files, and add `sql` to
  the format globs in CI docs checks and lefthook format-general.
- **BREAKING** for branch protection: the required status check name changes
  from the single `ci-success` to four per-workflow gates (`lint-success`,
  `build-success`, `test-success`, `security-success`).

## Capabilities

### New Capabilities

- `git-hooks`: Local lefthook stages — pre-commit auto-fix/format, pre-push
  typecheck/build, and secret scanning across commit, push, and CI.

### Modified Capabilities

- `ci-pipeline`: CI is split into four independent workflows with per-service
  matrix change detection via a composite action; build/test/lint concerns are
  separated; security scanning becomes a standalone always-run workflow; the
  aggregate gate becomes per-workflow.

## Impact

- **Workflows**: remove `.github/workflows/ci.yml`; add `lint.yml`, `build.yml`,
  `test.yml`, `security.yml`, `.github/actions/detect-changes/action.yml`.
- **Git hooks**: rewrite `lefthook.yml` stages.
- **Dependencies**: add `prettier-plugin-sql` to root `package.json`; register
  it in `.prettierrc.json`; extend Prettier globs to include `sql`.
- **Branch protection**: required checks must be updated from `ci-success` to
  the four new per-workflow gates.
- **Detection inputs**: `services/shared`, `services/proto`, `build-logic/`,
  `gradle/libs.versions.toml` become fan-out triggers for all services.
