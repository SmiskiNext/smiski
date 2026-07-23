# release-pipeline Specification

## Purpose

Defines the automated release pipeline for the monorepo: a manually dispatched
backend release workflow (version derivation, quality gate, container image
publishing, structured changelog with an AI editing pass, tag and GitHub Release
creation, and workflow hardening) and the API-derived TypeScript SDK lifecycle
(generation from the merged OpenAPI contract, public Zod v4 schemas, runtime
request/response validation, continuous validation, Changesets versioning, and
publication to GitHub Packages).

## Requirements

### Requirement: Release workflow trigger

The backend release SHALL be a dedicated GitHub Actions workflow triggered only
by `workflow_dispatch`. Its release jobs SHALL proceed only when the dispatched
ref is `refs/heads/dev`. It SHALL expose an optional `bump` choice accepting
`auto`, `major`, `minor`, or `patch`, with `auto` as the default. The workflow
SHALL NOT run on `push`, `pull_request`, or `schedule` events.

#### Scenario: Manual dispatch on dev

- **WHEN** a maintainer dispatches the release workflow from the `dev` branch
- **THEN** the workflow starts and proceeds to resolve a version

#### Scenario: Bump input omitted

- **WHEN** the workflow is dispatched without a `bump` value
- **THEN** the workflow proceeds using the version derived from conventional
  commits (see version derivation)

#### Scenario: Auto bump selected

- **WHEN** the workflow is dispatched with `bump=auto`
- **THEN** the workflow proceeds using the version derived from conventional
  commits

#### Scenario: Dispatch from a non-dev ref

- **WHEN** a maintainer dispatches the backend release workflow from any ref
  other than `refs/heads/dev`
- **THEN** release jobs do not resolve a version, run gates, publish images,
  create a tag, or create a GitHub Release

#### Scenario: No automatic trigger

- **WHEN** a commit is pushed to `dev` or a pull request is opened
- **THEN** the release workflow does not run

### Requirement: Version derivation

The workflow SHALL derive the next semantic version from conventional commits
since the most recent `v*` tag using git-cliff. When no `v*` tag exists, the
baseline SHALL be `0.0.0`. `feat` commits SHALL raise the minor level, `fix`
commits SHALL raise the patch level, and a breaking change (`!` or
`BREAKING CHANGE`) SHALL raise the major level. When `bump` is `auto` or
omitted, the workflow SHALL use that derived version. When `bump` is explicitly
`major`, `minor`, or `patch`, it SHALL override derivation and select that bump
against the latest tag. The resolved version SHALL be a valid `X.Y.Z` string,
and the tag form SHALL be `vX.Y.Z`.

#### Scenario: Derive from commits

- **WHEN** the latest tag is `v1.2.3` and the commits since it include a `feat`
  but no breaking change, and `bump=auto` or no `bump` input is given
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

### Requirement: API-derived TypeScript SDK generation

The repository SHALL generate `@smiskinext/smiski-ts` from the merged
`services/openapi.yaml` contract using the pinned `@hey-api/openapi-ts` 0.90.10
generator. Generation SHALL emit TypeScript operations and types for every
operation in the merged contract. Regeneration SHALL replace stale generated
output rather than preserve files that are no longer produced by the contract.

#### Scenario: Generate from the merged contract

- **WHEN** the SDK generation command runs
- **THEN** its input is `services/openapi.yaml` and its generated output
  contains the operations and types represented by that contract

#### Scenario: Remove stale generated output

- **WHEN** an operation is removed from the merged OpenAPI contract and the SDK
  is regenerated
- **THEN** generated files no longer export that operation or its generated
  types

### Requirement: Public Zod v4 schemas

SDK generation SHALL produce Zod v4 schemas for the generated API models and
operation data. The package's public root entry point SHALL export those schemas
alongside the generated operations and types. `zod` SHALL be a runtime package
dependency so an installed SDK can execute the exported schemas and operation
validators without relying on consumer development dependencies.

#### Scenario: Consumer imports a generated schema

- **WHEN** a consumer installs the packed SDK and imports a generated schema
  such as `zMeetScheduleMeetingRequest` from `@smiskinext/smiski-ts`
- **THEN** the imported value exposes the Zod parsing API at runtime

#### Scenario: Runtime dependency is installed

- **WHEN** the SDK tarball is installed in a clean consumer project
- **THEN** its Zod schemas and validators resolve `zod` without the consumer
  declaring `zod` separately

### Requirement: Runtime request and response validation

Every generated SDK operation SHALL validate its complete request data with its
generated Zod request schema before invoking the transport. Every operation
SHALL validate a successful response with its generated Zod response schema
before returning it to the caller. Validation failures SHALL reject with a Zod
validation error and SHALL NOT return unvalidated data.

#### Scenario: Invalid request is rejected before fetch

- **WHEN** a caller invokes an SDK operation with request data that violates its
  generated request schema
- **THEN** the operation rejects with a Zod validation error and does not invoke
  `fetch`

#### Scenario: Malformed successful response is rejected

- **WHEN** the transport returns a 2xx response whose body violates the
  operation's generated response schema
- **THEN** the operation rejects with a Zod validation error instead of
  returning the malformed body

#### Scenario: Every generated operation has both validators

- **WHEN** generated SDK operations are inspected
- **THEN** each operation is wired to one generated request validator and one
  generated response validator

### Requirement: SDK continuous validation

The SDK validation workflow SHALL run for relevant pull requests and pushes. It
SHALL regenerate service and merged OpenAPI documents, regenerate the SDK, and
fail if tracked OpenAPI or generated SDK files differ. After the drift check it
SHALL typecheck and build the SDK, pack the distributable tarball, install that
tarball in a clean temporary consumer project, and execute smoke assertions for
the public schema export and request/response validation behavior.

#### Scenario: Generated drift is detected

- **WHEN** regeneration changes a tracked service OpenAPI document, the merged
  OpenAPI document, or `sdks/typescript/src/generated`
- **THEN** SDK validation fails at the generated-file drift check

#### Scenario: Packed artifact passes consumer smoke test

- **WHEN** generation is current and the SDK typecheck and build pass
- **THEN** CI installs the packed tarball in a clean temporary project and
  verifies a public schema, pre-fetch request rejection, and malformed-2xx
  response rejection

### Requirement: SDK release trigger and Changesets versioning

SDK release automation SHALL be distinct from the manually dispatched backend
release. The SDK release workflow SHALL run on pushes to `dev` and SHALL use
Changesets metadata for `@smiskinext/smiski-ts`. When unreleased changesets are
present, it SHALL create or update a release pull request targeting `dev`. The
release pull request SHALL apply the requested semantic version changes and
consume the included changesets.

#### Scenario: Push with an unreleased changeset

- **WHEN** a commit containing an unreleased SDK changeset reaches `dev`
- **THEN** the SDK release workflow creates or updates a Changesets release pull
  request for `@smiskinext/smiski-ts`

#### Scenario: Backend release remains manual

- **WHEN** a normal commit is pushed to `dev`
- **THEN** the SDK release workflow may run, but the backend release workflow is
  not automatically dispatched

### Requirement: SDK publication to GitHub Packages

SDK release automation SHALL, after a Changesets release pull request is merged
to `dev`, install all required dependencies from a clean checkout, build the
versioned SDK, and execute the root `release:sdk` command. That command SHALL
publish `@smiskinext/smiski-ts` to `https://npm.pkg.github.com` with restricted
access using the workflow token, and SHALL fail rather than silently succeed if
versioning, build, authentication, or publication fails. The root package
scripts, Changesets dependency, lockfiles, and pnpm workspace behavior SHALL be
internally consistent with this clean-checkout path.

#### Scenario: Release pull request is merged

- **WHEN** a Changesets release pull request is merged to `dev`
- **THEN** a clean workflow checkout can install dependencies, invoke
  `pnpm release:sdk`, and publish the versioned SDK to restricted GitHub
  Packages

#### Scenario: Release command is unavailable

- **WHEN** the workflow cannot resolve the root `release:sdk` command or the
  Changesets CLI from a clean checkout
- **THEN** the SDK release job fails and does not report a successful publish

#### Scenario: Package-manager model is coherent

- **WHEN** root SDK validation and release commands run from a clean checkout
- **THEN** workspace membership, `--ignore-workspace` usage, and lockfile
  selection follow one documented dependency model and install the same required
  SDK and Changesets dependencies
