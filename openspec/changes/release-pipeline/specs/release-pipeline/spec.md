# release-pipeline Specification

## ADDED Requirements

### Requirement: Release workflow trigger

The release SHALL be a dedicated GitHub Actions workflow triggered only by
`workflow_dispatch` on the `dev` branch. It SHALL expose an optional `bump`
input accepting exactly one of `major`, `minor`, or `patch`. The workflow SHALL
NOT run on `push`, `pull_request`, or `schedule` events.

#### Scenario: Manual dispatch on dev

- **WHEN** a maintainer dispatches the release workflow from the `dev` branch
- **THEN** the workflow starts and proceeds to resolve a version

#### Scenario: Bump input omitted

- **WHEN** the workflow is dispatched without a `bump` value
- **THEN** the workflow proceeds using the version derived from conventional
  commits (see version derivation)

#### Scenario: No automatic trigger

- **WHEN** a commit is pushed to `dev` or a pull request is opened
- **THEN** the release workflow does not run

### Requirement: Version derivation

The workflow SHALL derive the next semantic version from conventional commits
since the most recent `v*` tag using git-cliff. When no `v*` tag exists, the
baseline SHALL be `0.0.0`. `feat` commits SHALL raise the minor level, `fix`
commits SHALL raise the patch level, and a breaking change (`!` or
`BREAKING CHANGE`) SHALL raise the major level. When the `bump` input is
provided, it SHALL override the derived level and select that bump against the
latest tag. The resolved version SHALL be a valid `X.Y.Z` string, and the tag
form SHALL be `vX.Y.Z`.

#### Scenario: Derive from commits

- **WHEN** the latest tag is `v1.2.3` and the commits since it include a `feat`
  but no breaking change, and no `bump` input is given
- **THEN** the resolved version is `1.3.0`

#### Scenario: Manual bump overrides derivation

- **WHEN** the latest tag is `v1.2.3` and the maintainer selects `bump=major`
- **THEN** the resolved version is `2.0.0` regardless of the commit types

#### Scenario: First release with no tags

- **WHEN** no `v*` tag exists and `bump=patch` (or derivation yields a patch)
- **THEN** the resolved version is `0.0.1`

### Requirement: Single version source of truth

Service versions SHALL be resolved from a single source: the `build-logic`
service convention plugins SHALL set the project version from the `VERSION`
environment variable, falling back to `0.0.1-SNAPSHOT` when it is unset. The
hardcoded `version` assignment SHALL be removed from the root `build.gradle.kts`
and from every service `build.gradle.kts` (`tenant`, `meet`, `record`,
`notification`, `shared`, `proto`). Because the services are composite builds,
the value SHALL propagate via the environment variable rather than a project
property passed from the root build.

#### Scenario: Default local build

- **WHEN** a service is built without the `VERSION` environment variable set
- **THEN** the resolved project version is `0.0.1-SNAPSHOT`

#### Scenario: Version injected for release

- **WHEN** a service is built with `VERSION=1.4.0` in the environment
- **THEN** the resolved project version is `1.4.0` for every included service
  build

#### Scenario: No stray hardcoded version remains

- **WHEN** the root or any service `build.gradle.kts` is inspected
- **THEN** no `version = "0.0.1-SNAPSHOT"` assignment is present in those files

### Requirement: Release quality gate

Before publishing any artifact, the workflow SHALL run
`./services/gradlew -p services/<name> assemble test integrationTest` for all
four backend services (`tenant`, `meet`, `record`, `notification`) on a
Docker-enabled runner. The workflow SHALL NOT build or push any image, create
any tag, or create any release if any of these tasks fail.

#### Scenario: All services pass

- **WHEN** assemble and the `test`/`integrationTest` source sets pass for all
  four services
- **THEN** the workflow proceeds to image publishing

#### Scenario: A service fails its gate

- **WHEN** any unit, ArchUnit, or integration test fails for any service
- **THEN** the workflow fails and no image, tag, or release is produced

### Requirement: Container image publishing

For each of the four services, the workflow SHALL build the image with Spring
Boot `bootBuildImage` using the resolved version, then push it to
`ghcr.io/smiskinext/<service>` tagged with both the resolved `X.Y.Z` and
`latest`. The workflow SHALL authenticate to GHCR before pushing. All four
service images SHALL carry the same resolved version.

#### Scenario: Image pushed with both tags

- **WHEN** the version resolves to `1.4.0` and the gate has passed
- **THEN** each service image is available at
  `ghcr.io/smiskinext/<service>:1.4.0` and `ghcr.io/smiskinext/<service>:latest`

#### Scenario: Synchronized versions

- **WHEN** a release publishes images for all services
- **THEN** every service image shares the same `X.Y.Z` tag

#### Scenario: Push failure aborts the release

- **WHEN** authentication to GHCR fails or an image push fails
- **THEN** the workflow fails and does not create the git tag or GitHub Release

### Requirement: Deterministic structured changelog

The workflow SHALL generate a structured changelog with git-cliff using the
repository `cliff.toml`. Entries SHALL be grouped into three top-level sections
in this order: `Services` (commits scoped to `tenant`, `meet`, `record`, or
`notification`), `App` (commits scoped to `app`), and `Other` (all remaining
conventional commits, including unscoped ones). Within a section, when both
non-fix and fix commits are present, they SHALL be split under `Improvements`
and `Bugfixes`; when only non-fix commits are present, entries SHALL be listed
directly without subheaders. Each entry SHALL show the short commit hash and the
commit message. No contributor thank-you block SHALL be produced.

#### Scenario: Service commit grouped under Services

- **WHEN** a commit `feat(meet): add listing` exists since the last tag
- **THEN** it appears under the `Services` section

#### Scenario: Unscoped commit grouped under Other

- **WHEN** a commit `feat: add create instant meet` (no scope) exists since the
  last tag
- **THEN** it appears under the `Other` section

#### Scenario: Improvements and bugfixes split

- **WHEN** a section contains both `feat` and `fix` commits
- **THEN** the section shows an `Improvements` subgroup and a `Bugfixes`
  subgroup

#### Scenario: No thank-you block

- **WHEN** the changelog is generated
- **THEN** no contributor thank-you block is included in the output

### Requirement: AI changelog editing pass

The workflow SHALL run an AI editing pass over the structured changelog using
`opencode run --command changelog` with a CI-scoped Anthropic-compatible
provider, writing the result to `UPCOMING_CHANGELOG.md`. The provider credential
SHALL be supplied from a GitHub Actions secret referenced via `{env:...}` and
SHALL NOT be hardcoded. The `opencode` CLI SHALL be pinned to a version whose
`run` command returns a non-zero exit code on failure
(`opencode-ai >= 1.17.20`), and the AI job SHALL have a bounded
`timeout-minutes`. If the AI step fails or produces an empty
`UPCOMING_CHANGELOG.md`, the workflow SHALL fail and SHALL NOT create the git
tag or GitHub Release. `UPCOMING_CHANGELOG.md` SHALL NOT be committed to the
repository.

#### Scenario: Successful edit produces notes

- **WHEN** the AI editing pass completes and `UPCOMING_CHANGELOG.md` is
  non-empty
- **THEN** its content is used as the GitHub Release body

#### Scenario: AI failure aborts the release

- **WHEN** `opencode run` exits non-zero or `UPCOMING_CHANGELOG.md` is empty
- **THEN** the workflow fails and no git tag or GitHub Release is created

#### Scenario: Credential is not hardcoded

- **WHEN** the CI provider configuration is inspected
- **THEN** the API key is referenced via `{env:...}` and no secret value is
  embedded in tracked files

#### Scenario: Changelog file is not committed

- **WHEN** a release completes
- **THEN** `UPCOMING_CHANGELOG.md` is not added to the repository history

### Requirement: Tag and GitHub Release creation

After images are published and release notes are generated, the workflow SHALL
create the git tag `vX.Y.Z` pointing at the dispatched commit, push it, and
create a GitHub Release for that tag using the edited `UPCOMING_CHANGELOG.md` as
the body. Tag and release creation SHALL be the final steps, occurring only
after the gate, image publishing, and changelog steps all succeed.

#### Scenario: Tag and release created last

- **WHEN** the gate passed, all images were pushed, and notes were generated
- **THEN** the workflow creates git tag `vX.Y.Z` and a GitHub Release with the
  edited notes as its body

#### Scenario: No partial release on earlier failure

- **WHEN** any prior step (gate, image push, or changelog) fails
- **THEN** the git tag and GitHub Release are not created

### Requirement: Release workflow hardening

The release workflow SHALL pin all non-GitHub-owned actions to a full commit
SHA, request only the token scopes it needs (`contents: write` for tag and
release creation, `packages: write` for GHCR push), and apply concurrency
control that groups runs without cancelling an in-progress release.

#### Scenario: Actions are SHA-pinned

- **WHEN** the workflow references any non-GitHub-owned action
- **THEN** the reference uses a full commit SHA

#### Scenario: Scoped permissions

- **WHEN** the workflow runs
- **THEN** the granted token scopes are limited to `contents: write` and
  `packages: write`

#### Scenario: In-progress release is not cancelled

- **WHEN** a release run is in progress and another dispatch occurs for the same
  concurrency group
- **THEN** the in-progress run is allowed to complete rather than being
  cancelled
