# Implementation Tasks

## 1. Prettier SQL support

- [x] 1.1 Add `prettier-plugin-sql` to root `package.json` devDependencies and
      run `pnpm install`
- [x] 1.2 Register `prettier-plugin-sql` in `.prettierrc.json` `plugins` array
      (keep existing toml/sh plugins)
- [x] 1.3 Extend the root `format` script glob and any Prettier globs to include
      `sql`
- [x] 1.4 Run `pnpm exec prettier --check '**/*.sql'` on the three Flyway
      baselines; verify only whitespace changes, no semantic diffs

## 2. Detect-changes composite action

- [x] 2.1 Create `.github/actions/detect-changes/action.yml` as a composite
      action wrapping `dorny/paths-filter` (SHA-pinned)
- [x] 2.2 Define per-service filters (`tenant`, `meet`, `record`,
      `notification`) each including its own dir plus fan-out inputs
      `services/shared/**`, `services/proto/**`, `build-logic/**`,
      `gradle/libs.versions.toml`
- [x] 2.3 Emit `services` output as a JSON array of changed service names for
      matrix consumption
- [x] 2.4 Emit boolean outputs `app`, `scripts`, `proto`, `docs`
- [x] 2.5 Emit `openapi_services` output as the subset of changed services
      limited to `tenant`, `meet`, `record`

## 3. Lint workflow

- [x] 3.1 Create `.github/workflows/lint.yml` with triggers
      (`pull_request`/`push` on `dev`,`main`), read-only permissions,
      concurrency cancel-in-progress
- [x] 3.2 Add a detect-changes step using the composite action; expose outputs
      to downstream jobs
- [x] 3.3 Java Spotless check job: matrix over `services`, run `spotlessCheck`
      per service
- [x] 3.4 App/scripts Biome lint jobs gated on `app`/`scripts` outputs
- [x] 3.5 Proto job gated on `proto`: `buf lint` + `buf breaking` against `dev`
      (checkout `fetch-depth: 0`)
- [x] 3.6 Docs job gated on `docs`: markdownlint + Prettier `--check` on
      `md/json/toml/yaml/yml/sh/sql`
- [x] 3.7 Add `lint-success` aggregate gate (`if: always()`, fail on any
      `failure`/`cancelled`)

## 4. Build workflow

- [x] 4.1 Create `.github/workflows/build.yml` with same
      triggers/permissions/concurrency and detect-changes step
- [x] 4.2 Java build job: matrix over `services`, run
      `./services/gradlew -p services/<name> assemble` (no tests); skip cleanly
      when matrix empty
- [x] 4.3 App build job gated on `app`: `tsc --noEmit`, build
      `static/hello-world`, `forge lint`; no deploy/install
- [x] 4.4 Scripts build job gated on `scripts`: `tsc --noEmit`
- [x] 4.5 Add mise provisioning + Gradle and pnpm-store caching to relevant jobs
- [x] 4.6 Add `build-success` aggregate gate

## 5. Test workflow

- [x] 5.1 Create `.github/workflows/test.yml` with same
      triggers/permissions/concurrency and detect-changes step
- [x] 5.2 Java test job: matrix over `services`, run
      `./services/gradlew -p services/<name> test integrationTest` on
      Docker-enabled runner
- [x] 5.3 Enforce `jacocoTestCoverageVerification` per service and upload JaCoCo
      report artifact (`if: always()`)
- [x] 5.4 OpenAPI drift job over `openapi_services`: regenerate spec, fail on
      diff with actionable message, run `openapi:lint` (Redocly)
- [x] 5.5 Add `test-success` aggregate gate

## 6. Security workflow

- [x] 6.1 Create `.github/workflows/security.yml` that always runs
      `gitleaks detect` with `.gitleaks.toml`, independent of change detection
      (`fetch-depth: 0`, BASE/HEAD SHA logic)
- [x] 6.2 Add `security-success` aggregate gate

## 7. Retire monolithic CI

- [x] 7.1 Delete `.github/workflows/ci.yml`
- [x] 7.2 Document the branch-protection required-check change (`ci-success` →
      `lint-success`, `build-success`, `test-success`, `security-success`) in
      the proposal Impact / PR description

## 8. Lefthook rework

- [x] 8.1 pre-commit: keep `gitleaks protect --staged`; keep only
      auto-fix/format commands (spotlessApply, buf format, prettier `--write`
      incl. sql, markdownlint `--fix`, biome `check --write`); remove
      lint/typecheck commands
- [x] 8.2 pre-push: keep `block-main` and `gitleaks detect`; add `tsc --noEmit`
      for scripts and app; add Java `assemble` per changed service; remove lint
      (no-fix), format-verify, and any react build
- [x] 8.3 Ensure `format-general` glob includes `sql` at pre-commit
- [x] 8.4 Keep commit-msg commitlint unchanged

## 9. Verification

- [x] 9.1 Validate all workflow YAML (`actionlint` if available) and confirm
      lefthook config parses (`lefthook validate` or dry-run)
- [x] 9.2 Simulate path scenarios: single-service, shared fan-out, docs-only,
      app-only — confirm correct jobs run/skip and gates pass
- [x] 9.3 Confirm all third-party actions are SHA-pinned and permissions default
      to read-only
