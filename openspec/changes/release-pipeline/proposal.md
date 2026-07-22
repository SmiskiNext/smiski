## Why

The repository can validate code (lint, build, test, security) but has no way to
cut a release: service versions are hardcoded to `0.0.1-SNAPSHOT` in nine
places, no container images are published, no git tags or GitHub Releases are
produced, and the pre-configured `cliff.toml` changelog tooling is never
invoked. Shipping the four backend services to `ghcr.io/smiskinext` requires a
repeatable, auditable release path that turns a set of merged conventional
commits into versioned, published artifacts.

## What Changes

- Add a manually-triggered `release` GitHub Actions workflow
  (`workflow_dispatch` on `dev`) with an optional `bump` input
  (major/minor/patch).
- Derive the next version automatically from conventional commits since the last
  tag via git-cliff, allowing the `bump` input to override the derived level.
- Establish a single version source of truth: the `build-logic` service
  convention plugins read the version from the `VERSION` environment variable
  (falling back to `0.0.1-SNAPSHOT`). **BREAKING** for build config: the
  hardcoded `version = "0.0.1-SNAPSHOT"` lines are removed from the root and all
  seven service `build.gradle.kts` files.
- Gate every release on `assemble` + `test integrationTest` for all four
  services (`tenant`, `meet`, `record`, `notification`).
- Build each service image with Spring Boot `bootBuildImage` and push it to
  `ghcr.io/smiskinext/<service>` with two tags: the resolved `X.Y.Z` and
  `latest`.
- Generate release notes with a two-layer changelog: git-cliff produces a
  deterministic structured changelog (grouped into Services / App / Other), then
  an AI editing pass (`opencode run --command changelog`, Anthropic-compatible
  provider) rewrites the wording for readers.
- Create the git tag `vX.Y.Z` and a GitHub Release carrying the edited notes.

## Capabilities

### New Capabilities

- `release-pipeline`: Manual, versioned release of the backend services —
  version derivation and single-source versioning, quality gating, container
  image publishing to GHCR, two-layer changelog generation, and git tag + GitHub
  Release creation.

### Modified Capabilities

<!-- No spec-level requirement changes to existing capabilities. The ci-pipeline
     spec remains validation-only; release concerns live in the new capability. -->

## Impact

- **New**: `.github/workflows/release.yml`, `.opencode/command/changelog.md`, a
  CI-scoped opencode provider config (Anthropic-compatible), and
  `openspec/specs/release-pipeline/spec.md`.
- **Modified**: `cliff.toml` (regrouped sections + bumped-version support);
  `build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.base.gradle.kts`
  and `...service.mongodb.base.gradle.kts` (version from `VERSION` env).
- **Removed**: `version = "0.0.1-SNAPSHOT"` from root `build.gradle.kts` and
  `services/{tenant,meet,record,notification,shared,proto}/build.gradle.kts`.
- **Dependencies/secrets**: git-cliff and the `opencode` CLI (`opencode-ai`
  pinned `>=1.17.20`) in CI; an Anthropic-compatible API key supplied as a
  GitHub Actions secret referenced via `{env:...}`; `GITHUB_TOKEN` with
  `contents: write` and `packages: write`.
- **Out of scope**: k8s manifest image references (`ghcr.io/phunguy65/zms/...`),
  auto-release on push, committing a `CHANGELOG.md`, contributor thank-you
  blocks, and the `package.json` version.
