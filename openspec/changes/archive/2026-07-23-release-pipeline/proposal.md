## Why

The repository needs two explicit release paths. The backend path must turn
merged conventional commits into versioned service images, while the API client
path must turn the merged OpenAPI contract into a validated, publishable
package. The TypeScript SDK structure, generated client, Zod schemas, and
validation workflow are now present, but its Changesets-based publishing wiring
is incomplete. The change must describe the verified SDK capabilities without
claiming that SDK publication already works end to end.

## What Changes

- Keep the backend release as a manually-triggered `release` GitHub Actions
  workflow (`workflow_dispatch`) whose release jobs are restricted to the `dev`
  ref. Its optional `bump` choice accepts `auto`, `major`, `minor`, or `patch`
  and defaults to `auto`.
- Derive the next version automatically from conventional commits since the last
  tag via git-cliff when `bump` is `auto` or omitted; an explicit `major`,
  `minor`, or `patch` selection overrides the derived level.
- Establish a single version source of truth: the `build-logic` service
  convention plugins read the version from the `VERSION` environment variable
  (falling back to `0.0.1-SNAPSHOT`). **BREAKING** for build config: the
  hardcoded `version = "0.0.1-SNAPSHOT"` lines are removed from the root and all
  six service `build.gradle.kts` files (`tenant`, `meet`, `record`,
  `notification`, `shared`, `proto`).
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
- Maintain an API-derived TypeScript SDK in `sdks/typescript`, generated from
  the merged `services/openapi.yaml` using `@hey-api/openapi-ts` 0.90.10.
- Export generated TypeScript types, operations, and Zod v4 schemas from the
  public SDK entry point. Generated operations SHALL validate both request and
  response data at runtime; `zod` is a runtime dependency.
- Validate the SDK in CI by regenerating the OpenAPI documents and SDK, checking
  generated-file drift, running TypeScript typecheck and build, and running a
  tarball smoke test that covers public schemas and request/response rejection.
- Define the intended SDK release model: pushes to `dev` invoke Changesets to
  create or update a release PR, and the publish step publishes the versioned
  package to restricted GitHub Packages.
- Track the SDK release wiring that is still incomplete: the package-manager
  model is inconsistent, root Changesets scripts and dependency are missing, and
  a clean-checkout end-to-end publish has not been verified.

## Capabilities

### New Capabilities

- `release-pipeline`: Manual, versioned release of the backend services —
  version derivation and single-source versioning, quality gating, container
  image publishing to GHCR, two-layer changelog generation, and git tag + GitHub
  Release creation. It also defines API-derived TypeScript SDK generation,
  runtime validation, CI drift protection, and the target Changesets/GitHub
  Packages release behavior.

### Modified Capabilities

<!-- No spec-level requirement changes to existing capabilities. The ci-pipeline
     spec remains validation-only; release concerns live in the new capability. -->

## Impact

- **New**: `.github/workflows/release.yml`, `.github/workflows/sdk-release.yml`,
  `.github/workflows/sdk.yml`, `.opencode/command/changelog.md`, a CI-scoped
  opencode provider config (Anthropic-compatible), and the TypeScript SDK under
  `sdks/typescript`.
- **Modified**: `cliff.toml` (regrouped sections + bumped-version support);
  `build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.base.gradle.kts`
  and `...service.mongodb.base.gradle.kts` (version from `VERSION` env).
- **Removed**: `version = "0.0.1-SNAPSHOT"` from root `build.gradle.kts` and
  `services/{tenant,meet,record,notification,shared,proto}/build.gradle.kts`.
- **Dependencies/secrets**: git-cliff and the `opencode` CLI (`opencode-ai`
  pinned `>=1.17.20`) in backend release CI; an Anthropic-compatible API key
  supplied as a GitHub Actions secret referenced via `{env:...}`; and
  `GITHUB_TOKEN` with the scopes required for backend releases and restricted
  GitHub Packages publishing.
- **Known incomplete wiring**: root `package.json` does not yet provide the
  Changesets dependency/scripts, including `release:sdk`. The repository also
  includes `sdks/typescript` in `pnpm-workspace.yaml` while the root SDK scripts
  invoke it with `--ignore-workspace`; this dependency model must be resolved
  before claiming a complete clean-checkout release.
- **Known incomplete backend guard**: `.github/workflows/release.yml` is manual,
  but its jobs do not yet enforce `github.ref == 'refs/heads/dev'`; the target
  branch restriction is therefore not complete.
- **Not changed by this documentation update**: backend implementation,
  TypeScript SDK implementation, Kubernetes image references, or any README.
