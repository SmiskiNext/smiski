# ADDED Requirements

## Requirement: Release workflow SHALL rely on git-cliff action managed installation

The release workflow SHALL remove any manual git-cliff binary download and
extraction steps and SHALL use only `orhun/git-cliff-action@v4` for changelog
generation execution.

### Scenario: Release job executes changelog generation without manual install

- **WHEN** the release workflow runs the changelog generation phase
- **THEN** there is no preceding manual `curl | tar` git-cliff install step and
  changelog generation is performed by `orhun/git-cliff-action@v4`

## Requirement: Android workflow SHALL validate secret before decoding

The Android workflow SHALL validate that `GOOGLE_SERVICES_JSON_BASE64` is
non-empty before running base64 decode and SHALL fail with a clear configuration
error if missing.

### Scenario: Android workflow fails early for missing secret

- **WHEN** `GOOGLE_SERVICES_JSON_BASE64` is empty or unset during workflow
  execution
- **THEN** the workflow fails before decode with an explicit message indicating
  the repository secret must be configured

### Scenario: Android workflow continues when secret is present

- **WHEN** `GOOGLE_SERVICES_JSON_BASE64` is non-empty
- **THEN** the existing decode step runs and writes
  `frontends/android-app/app/google-services.json`

## Requirement: CI workflows SHALL use supported action major versions

All workflow files under `.github/workflows/` SHALL upgrade the specified action
dependencies to supported major versions: `actions/checkout@v6`,
`actions/setup-node@v6`, `pnpm/action-setup@v6`, `actions/setup-java@v5`,
`gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7` where
applicable.

### Scenario: Workflow action references are upgraded consistently

- **WHEN** workflow files are inspected after the change
- **THEN** no applicable references remain on the deprecated majors listed in
  scope and updated majors are used consistently across all five workflows

## Requirement: Web dependency lockfile SHALL match package manifests

The repository lockfile SHALL be regenerated to match current
`frontends/web/package.json` dependencies, and frozen-lockfile installation
SHALL succeed.

### Scenario: Frozen lockfile install passes after regeneration

- **WHEN** `pnpm install --frozen-lockfile` is executed after lockfile
  regeneration
- **THEN** installation succeeds without lockfile mismatch errors
