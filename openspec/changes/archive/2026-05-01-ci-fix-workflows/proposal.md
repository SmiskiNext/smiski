# Why

The CI pipeline currently fails in multiple workflows due to outdated action
versions, a broken git-cliff install URL, missing Android secret validation, and
a stale lockfile. These issues block reliable validation and release automation,
so they must be fixed now to restore CI stability and avoid upcoming Node.js 20
runtime deprecation failures.

## What Changes

- Remove the manual `curl | tar` git-cliff installation step from
  `.github/workflows/release.yml` because `orhun/git-cliff-action@v4` already
  manages the binary.
- Add a pre-decode validation guard in `.github/workflows/android.yml` to fail
  early with a clear message when `GOOGLE_SERVICES_JSON_BASE64` is empty.
- Regenerate `pnpm-lock.yaml` from the current dependency graph so CI lockfile
  checks align with `frontends/web/package.json`.
- Upgrade GitHub Actions references across all workflow files to supported
  non-Node 20 major versions:
    - `actions/checkout` `@v4` → `@v6`
    - `actions/setup-node` `@v4` → `@v6`
    - `pnpm/action-setup` `@v4` → `@v6`
    - `actions/setup-java` `@v4` → `@v5`
    - `gradle/actions/setup-gradle` `@v4` → `@v6`
    - `actions/upload-artifact` `@v4` → `@v7`
- Preserve existing workflow structure, triggers, and job topology.

## Capabilities

### New Capabilities

- `ci-workflow-maintenance`: Ensure CI workflows remain executable and supported
  by keeping action runtimes current, removing redundant/broken setup steps, and
  validating required secrets before dependent commands run.

### Modified Capabilities

- None.

## Impact

- Affected files: all workflow files under
  `/home/PNguyen/Workspace/MyProject/zero-meeting-system/.github/workflows/` and
  `/home/PNguyen/Workspace/MyProject/zero-meeting-system/pnpm-lock.yaml`.
- Affected systems: GitHub Actions CI for release, Android, web, and
  Gradle-based jobs.
- Dependencies/tooling impact: GitHub Actions major version bumps and lockfile
  synchronization with pnpm dependency manifests.
- Operational note: Repository secret value correction for
  `GOOGLE_SERVICES_JSON_BASE64` remains a manual GitHub settings task and is out
  of scope for this change.
