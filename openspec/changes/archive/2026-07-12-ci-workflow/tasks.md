# Tasks: ci-workflow

## 1. Workflow scaffold and hardening

- [x] 1.1 Create `.github/workflows/ci.yml` with `name: CI` and triggers:
      `pull_request` (branches `dev`, `main`) and `push` (branches `dev`,
      `main`)
- [x] 1.2 Set workflow-level `permissions: contents: read` (least privilege)
- [x] 1.3 Add `concurrency` group `ci-${{ github.workflow }}-${{ github.ref }}`
      with `cancel-in-progress: true`
- [x] 1.4 Pin every third-party action to a full commit SHA with a `# vX.Y.Z`
      comment (actions/checkout, jdx/mise-action, dorny/paths-filter,
      gradle/actions/setup-gradle, actions/cache, actions/upload-artifact) ←
      (verify: no action referenced by bare tag; all use 40-char SHA)

## 2. Change detection

- [x] 2.1 Add `changes` job using `dorny/paths-filter` with filters for
      `services` (exclude `services/proto/**`), `app`, `scripts`, `proto`
      (`services/proto/**`), and `docs` (md/json/toml/yaml/yml/sh + prettier/
      markdownlint config)
- [x] 2.2 Expose each filter as a job output consumed via
      `needs.changes.outputs.*` ← (verify: outputs wired; each downstream job
      has correct `if` guard)

## 3. Backend job

- [x] 3.1 Add `backend` job gated on `needs.changes.outputs.services == 'true'`,
      `runs-on: ubuntu-latest`, checkout + `jdx/mise-action` + pnpm store
      cache + `setup-gradle`
- [x] 3.2 Run `./services/gradlew build` (fast `test` + `integrationTest` via
      Testcontainers on preinstalled Docker; no manual service containers)
- [x] 3.3 Run `jacocoTestCoverageVerification` coverage gate (line 70 / branch
      60), generate `jacocoTestReport`, and upload the JaCoCo report via
      `actions/upload-artifact` (`if: always()`)
- [x] 3.4 Add OpenAPI drift step: `pnpm install --frozen-lockfile`,
      `pnpm run openapi:generate` then
      `git diff --exit-code -- 'services/**/openapi.yaml'` with a remediation
      message; run `pnpm run openapi:lint` (Redocly) ← (verify:
      build+coverage+drift+redocly all enforced; drift fails on stale spec)

## 4. Forge app job

- [x] 4.1 Add `forge-app` job gated on `needs.changes.outputs.app == 'true'`,
      checkout + mise + pnpm store cache + `pnpm install --frozen-lockfile`
- [x] 4.2 Run Biome check and `tsc --noEmit` for the app root
- [x] 4.3 Install (`npm ci`) and build `static/hello-world` (react-scripts,
      `CI=true`)
- [x] 4.4 Install Forge CLI (`npm i -g @forge/cli`) and run `forge lint` on
      `manifest.yml`; no deploy/install, no credentials ← (verify: UI build
      produces output, forge lint runs, no forge deploy present)

## 5. Scripts job

- [x] 5.1 Add `scripts` job gated on `needs.changes.outputs.scripts == 'true'`;
      run Biome check and `tsc --noEmit` for the CLI package

## 6. Proto job

- [x] 6.1 Add `proto` job gated on `needs.changes.outputs.proto == 'true'`,
      checkout with `fetch-depth: 0`, mise (buf)
- [x] 6.2 Run `buf lint` and `buf breaking --against '.git#branch=dev'` ←
      (verify: breaking compares against dev; full history fetched)

## 7. Security job

- [x] 7.1 Add `security` job that always runs (no path gate), checkout with
      `fetch-depth: 0`; run `gitleaks detect --config .gitleaks.toml`

## 8. Docs job

- [x] 8.1 Add `docs` job gated on `needs.changes.outputs.docs == 'true'`; run
      markdownlint and Prettier `--check` on md/json/toml/yaml/yml/sh

## 9. Aggregate gate

- [x] 9.1 Add `ci-success` job that `needs` all jobs (backend, forge-app,
      scripts, proto, security, docs), runs `if: always()`, and fails if any
      dependency result is `failure` or `cancelled`
      (`contains(needs.*.result, ...)`); passes when all are `success` or
      `skipped` ← (verify: skipped jobs do not fail the gate; a failing job
      fails the gate)

## 10. Validation

- [x] 10.1 Validate `openspec validate ci-workflow --strict`
- [x] 10.2 Lint the workflow YAML with `actionlint` (0 errors) ← (verify:
      workflow parses; job graph and `if` conditions are consistent)
- [x] 10.3 Document the manual follow-up: configure branch protection on `dev`
      and `main` to require only the `ci-success` check (PR description note,
      not a code change)
