## Why

The monorepo has a rich local quality gate (lefthook: gitleaks, Spotless, Buf,
Biome, Prettier, markdownlint, tsc) but **no server-side CI**. Nothing enforces
those gates on pull requests or protects the `dev`/`main` branches from broken
merges. Contributors can bypass local hooks (`--no-verify`), and there is no
shared, reproducible signal that backend tests, Forge app build, and OpenAPI
specs are healthy. This change adds a Continuous Integration pipeline so every
change is validated the same way, every time.

## What Changes

- Add a single GitHub Actions workflow (`.github/workflows/ci.yml`) triggered on
  `pull_request` and `push` targeting `dev` and `main`.
- Detect changed components (services / app / scripts / proto / docs) and run
  only the affected jobs (monorepo path filtering).
- Provision the toolchain on every runner via `jdx/mise-action`, reading the
  existing `.mise.toml` as the single source of truth (Java 25, Node, pnpm, Buf,
  gitleaks) so CI versions match local exactly.
- Backend job: `./services/gradlew build` (fast `test` + container-backed
  `integrationTest` via Testcontainers on the Docker-enabled runner), the
  `jacocoTestCoverageVerification` coverage gate (line ≥ 70% / branch ≥ 60%), a
  JaCoCo report upload, and an **OpenAPI drift check** (regenerate specs, fail
  if `openapi.yaml` differs from committed) plus Redocly lint.
- Forge app job: Biome check, `tsc --noEmit`, build `static/hello-world`
  (react-scripts), and `forge lint` on `manifest.yml`.
- Scripts job: Biome check + `tsc --noEmit`.
- Proto job: `buf lint` + `buf breaking` (against `dev`).
- Security job (always runs): `gitleaks detect` full-history scan.
- Docs job: markdownlint + Prettier `--check`.
- DevOps hardening: all third-party actions pinned by commit SHA,
  least-privilege `permissions: contents: read`, `concurrency`
  cancel-in-progress per ref, and Gradle + pnpm store caching.
- A final `ci-success` aggregate gate job so branch protection can require a
  single check while path-filtered jobs may be skipped.
- Mutation testing (PIT) and coverage/mutation _threshold ratcheting_ stay
  opt-in and are **not** wired into CI (preserves the existing "ratchet upward"
  design).
- Out of scope (**not** included): publishing Docker images to GHCR, deploying
  to Kubernetes, and configuring GitHub branch-protection settings (documented
  as a follow-up only).

## Capabilities

### New Capabilities

- `ci-pipeline`: Server-side continuous integration for the monorepo — trigger
  rules, change detection / path filtering, per-component validation jobs
  (backend, Forge app, scripts, proto, security, docs), toolchain provisioning,
  quality gates (tests, coverage, OpenAPI drift), the aggregate success gate,
  and DevOps hardening requirements (SHA-pinned actions, least-privilege
  permissions, concurrency control, caching).

### Modified Capabilities

<!-- None. This adds a new CI capability; it does not change existing
     api-convention, db-schema, or test-architecture requirements. -->

## Impact

- **New files**: `.github/workflows/ci.yml`.
- **No changes** to service/app/scripts source, `.mise.toml`, `lefthook.yml`,
  Gradle build logic, or `redocly.yaml` — CI reuses existing commands and tool
  pins.
- **CI infrastructure**: GitHub-hosted `ubuntu-latest` runners (Docker
  preinstalled for Testcontainers).
- **Coverage gate risk**: wiring `jacocoTestCoverageVerification` as a required
  CI gate may fail on current code if suites are below line 70% / branch 60%.
  This intentionally surfaces existing coverage debt (the "ratchet" behavior);
  addressed in design.md.
- **Branch model**: default branch `dev`, `main` protected; workflow targets
  both. Branch-protection required-check configuration is a manual follow-up.
