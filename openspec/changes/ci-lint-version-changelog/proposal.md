# Why

The zero-meeting-system monorepo has no CI pipeline, no automated versioning,
and no changelog generation. Code quality checks (Spotless, Biome, Buf,
Prettier, commitlint) run only locally via lefthook, meaning formatting and test
failures are never caught in pull requests. With conventional commits already
enforced, the project is ready to layer on release automation that derives
version bumps and changelogs directly from commit history.

## What Changes

- Add `.github/workflows/lint.yml` — parallel lint jobs (Java Spotless, proto
  Buf, web Biome, general Prettier, commitlint) triggered on PRs to main
- Add `.github/workflows/backend-test.yml` — Gradle test run with JDK 25 and
  Gradle caching, path-filtered to `services/**`
- Add `.github/workflows/web-test.yml` — pnpm test run, path-filtered to
  `frontends/web/**`
- Add `.github/workflows/android.yml` — Spotless check + debug APK build with
  Firebase secret injection, path-filtered to `frontends/android-app/**`
- Add `.github/workflows/release.yml` — push-to-main trigger; generates
  CHANGELOG.md via git-cliff, bumps monorepo version in `package.json`, commits,
  tags, and creates a GitHub Release
- Add `cliff.toml` — git-cliff configuration for conventional commits changelog
  format
- Add `.actrc` — nektos/act defaults for local CI testing
- Update `.gitignore` — add `.env` and `.secrets/` entries

## Capabilities

### New Capabilities

- `ci-lint`: Automated pull-request lint and format validation across all
  monorepo layers (Java, proto, web, markdown/yaml/json, commit messages)
- `ci-test`: Per-layer automated test execution in CI gated by path filters
  (backend Gradle tests, web pnpm tests, Android debug build + APK artifact)
- `ci-release`: Push-to-main release pipeline — changelog generation, semver
  version bump, git tag, and GitHub Release creation

### Modified Capabilities

## Impact

- **CI infrastructure**: Adds `.github/workflows/` directory with five workflow
  files; no existing CI configuration is modified
- **Root `package.json`**: `version` field becomes the single source of truth
  for the monorepo version; bumped automatically by the release workflow
- **`.gitignore`**: Extended with `.env` and `.secrets/` patterns
- **New config files**: `cliff.toml` (git-cliff), `.actrc` (act local runner)
- **GitHub Secrets required**: `GOOGLE_SERVICES_JSON_BASE64` for Android
  workflow; `GITHUB_TOKEN` (built-in) for release tagging
- **Android builds**: CI requires Android SDK; workflow uses
  `catthehacker/ubuntu:full-latest` or an explicit SDK setup step
- **No breaking changes** to existing local tooling or application code
