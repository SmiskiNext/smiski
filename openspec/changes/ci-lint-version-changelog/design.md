# Context

The zero-meeting-system monorepo currently enforces code quality only at the
local level via lefthook pre-commit hooks. There is no CI pipeline, so
formatting failures and broken tests are invisible during code review. The
project already uses conventional commits (commitlint + lefthook), which makes
automated changelog generation and semver version bumping straightforward. The
monorepo hosts three independent build systems (Gradle for services, Gradle for
Android, pnpm for web), each requiring its own setup and caching strategy in CI.
A single shared version number in `package.json` will serve as the canonical
release identifier for the whole monorepo.

## Goals / Non-Goals

**Goals:**

- Gate every PR to `main` on lint, format, and test checks via GitHub Actions
- Parallel lint jobs to keep feedback latency low
- Path-filtered test workflows so only relevant layers run per PR
- Automated release pipeline on push to `main`: CHANGELOG generation, semver
  bump, git tag, and GitHub Release
- Single unified version number across the monorepo (root `package.json`)
- Gradle build caching via `gradle/actions/setup-gradle` to reduce CI time
- Local workflow verification via `act` + actionlint

**Non-Goals:**

- Docker image publishing or deployment automation
- Per-service versioning or independent release cadences
- Coverage reporting or test result artifact uploads (beyond APK)
- Migration of existing version references inside Gradle build files (they
  continue to use their own version strings; only root `package.json` is the
  canonical release version)
- Scheduled or nightly builds

## Decisions

### Workflow layout: one file per concern

Five workflows instead of a single monolithic file. Each workflow has a single
trigger and a clear scope, making it easier to read, re-run selectively, and
bypass in emergencies.

Alternative considered: a single workflow with conditional jobs. Rejected
because a single failure poisons the entire run, path filtering becomes
cumbersome, and job isolation is cleaner with separate files.

### Linting: parallel jobs in one workflow

All lint checks (Spotless, Buf, Biome, Prettier, commitlint) run as parallel
jobs inside `lint.yml`. Each job is independent; failures are reported
individually so authors know exactly what to fix.

Alternative considered: sequential steps in one job. Rejected because a Spotless
failure would hide Biome errors until the next push.

### Commitlint in CI: full PR commit range

The commitlint job fetches the full PR commit range
(`github.event.pull_request .base.sha` to `HEAD`) and validates every commit.
This mirrors the local lefthook hook behavior and ensures no commit sneaks
through without a valid conventional-commit message.

### Testing: path-filtered separate workflows

`backend-test.yml`, `web-test.yml`, and `android.yml` each filter on their
respective directory subtree. A PR touching only `frontends/web/**` will not
trigger a Gradle build. This reduces noise and CI minutes.

### Gradle caching: gradle/actions/setup-gradle@v4

Uses the official Gradle GitHub Action for caching the Gradle wrapper,
dependencies, and build outputs. This is the recommended approach as of Gradle
8+ and avoids manual cache key management.

Alternative considered: `actions/cache` with manual Gradle paths. Rejected as
more brittle and requiring ongoing maintenance.

### Android: ubuntu-latest + manual Android SDK setup

Uses `ubuntu-latest` with the `android-actions/setup-android` action (or the
built-in Android SDK on GitHub-hosted runners) rather than a custom image, to
avoid large image pulls that can be slower than SDK installation. Firebase
`google-services.json` is injected from a base64-encoded GitHub Secret
(`GOOGLE_SERVICES_JSON_BASE64`) decoded at runtime, never stored in the
repository.

Alternative considered: `catthehacker/ubuntu:full-latest` custom image which
includes Android SDK pre-installed. This is viable but ties CI to a third-party
image; ubuntu-latest + setup step is more transparent.

### Changelog: git-cliff

git-cliff reads `cliff.toml` and produces a conventional-commits-aware
`CHANGELOG.md`. It supports per-type grouping (feat, fix, perf, etc.) and
filters out chore/ci commits from the release notes.

Alternative considered: `conventional-changelog-cli` (Node). Rejected because
git-cliff is faster, has a simpler config format, and does not add a Node
dependency to the release job.

### Versioning: node script reading cliff output

The release workflow parses the latest commits since the previous tag to
determine which semver component to increment (fix → patch, feat → minor,
BREAKING CHANGE footer or `!` → major). The bump is written back to root
`package.json` via `npm version` (no git commit from npm, done separately).

Alternative considered: semantic-release. More fully-featured but opinionated,
requires several plugins, and changes the commit/tag convention. The lightweight
custom script keeps full control.

### Release commit: bot-attributed commit on main

After bumping `package.json` and generating `CHANGELOG.md`, the workflow commits
both files directly to `main` with `[skip ci]` in the message to prevent an
infinite trigger loop, then tags `vX.Y.Z` and creates a GitHub Release.

### Local CI testing: act + actionlint

`.actrc` provides nektos/act defaults (medium runner image, linux/amd64
architecture) so developers can run `act pull_request` locally before pushing.
actionlint validates workflow YAML syntax and expression types.

## Risks / Trade-offs

- [Risk] Release workflow commits directly to `main`, requiring push permissions
  for `GITHUB_TOKEN` → Mitigation: enable "Allow GitHub Actions to create and
  approve pull requests" and grant write contents permission in workflow
  permissions block; document in repo settings.
- [Risk] `[skip ci]` on release commit suppresses CI; a broken release commit
  could land undetected → Mitigation: release workflow itself runs lint as a
  prior step; release commit touches only `package.json` and `CHANGELOG.md`.
- [Risk] Android SDK setup on ubuntu-latest may be slow if GitHub changes
  pre-installed tool versions → Mitigation: pin action versions; monitor build
  times; fall back to custom image if consistently slow.
- [Risk] Gradle build cache grows unbounded over time on GitHub-hosted runners →
  Mitigation: `gradle/actions/setup-gradle` has built-in cache eviction; no
  additional configuration required.
- [Risk] commitlint CI check and local lefthook hook may diverge if
  `commitlint.config.js` changes → Mitigation: both read the same config file;
  no duplication of rules.
- [Risk] Version in root `package.json` is the only authoritative version; Java
  and Android build files still contain their own version strings → Mitigation:
  document clearly in README that root `package.json` version is the canonical
  release version; do not attempt to keep Gradle versions in sync (out of
  scope).

## Migration Plan

1. Create `.github/workflows/` directory and all five workflow files.
2. Add `cliff.toml` to repository root.
3. Add `.actrc` to repository root.
4. Update `.gitignore`.
5. Verify workflows parse correctly with actionlint locally.
6. Open a test PR to `main` to confirm `lint.yml` jobs trigger and pass.
7. Merge — `release.yml` runs, creates `v0.0.1` tag (or the next version if
   commits since last tag warrant a bump), produces CHANGELOG.md and GitHub
   Release.

Rollback: delete or disable individual workflow files; no application code is
modified.

## Open Questions

- Should `web-test.yml` run `pnpm --dir frontends/web test` with a vitest
  config, or is there a separate playwright/e2e step to add later? (Assumed:
  unit tests only for now; e2e is out of scope.)
- Should the Android workflow also run unit tests (`testDebugUnitTest`) in
  addition to `assembleDebug`? (Assumed: yes, add as a step before the APK build
  to catch logic failures early.)
