## Context

The repo has four Spring Boot microservices under `services/` wired as Gradle
**composite builds** (`includeBuild` in `services/settings.gradle.kts`), each
applying convention plugins from `build-logic/`. The backend release path uses
the existing version, image, git-cliff, and opencode design described below.

The merged `services/openapi.yaml` is also the source contract for a generated
TypeScript SDK at `sdks/typescript`. `@hey-api/openapi-ts` 0.90.10 generates the
client operations, types, and Zod v4 schemas. Its SDK plugin is configured for
Zod request and response validators, and all nine generated operations currently
contain both validators. `zod` is a runtime dependency and the package entry
point exports the generated schemas.

SDK CI (`.github/workflows/sdk.yml`) regenerates the OpenAPI documents and SDK,
checks generated-file drift, typechecks, builds, and runs a tarball smoke test.
The smoke test verifies a public Zod schema, rejects an invalid request before
`fetch`, and rejects a malformed successful response. Root SDK commands exist
and have passed, but they use `--ignore-workspace` even though
`pnpm-workspace.yaml` includes `sdks/typescript`.

The SDK release workflow runs on pushes to `dev` and calls Changesets' action.
The target model is a Changesets release PR followed by publishing the package
to restricted GitHub Packages. The current root `package.json` has no
`release:sdk` script, Changesets scripts, or Changesets dependency, so the
workflow cannot yet be claimed as a complete clean-checkout publishing path.

## Goals / Non-Goals

**Goals:**

- One repeatable, manually-triggered backend release path restricted to the
  `dev` ref that produces versioned service images plus a git tag and GitHub
  Release.
- A single version source of truth compatible with composite builds.
- Semantic version derivation from conventional commits for `auto` or omitted
  input, overridable by an explicit `major`, `minor`, or `patch` selection.
- An opencode-style two-layer changelog: deterministic structure (git-cliff)
  then AI wording (opencode run).
- A generated TypeScript SDK whose public API includes runtime Zod v4 schemas,
  with CI protection against OpenAPI or generated-code drift.
- A documented Changesets/GitHub Packages release model for the SDK, with the
  missing wiring explicitly tracked until it works from a clean checkout.

**Non-Goals:**

- Fixing k8s manifest image references (`ghcr.io/phunguy65/zms/...`).
- Auto-release of backend services on push or a fully automatic backend release.
  The SDK push-on-`dev` Changesets flow is intentional and is not this non-goal.
- Committing a `CHANGELOG.md` into the repo.
- Contributor thank-you blocks.
- Changing backend release trigger semantics: backend release remains manual,
  while SDK release follows the push/Changesets PR model.

## Decisions

### Version propagation via `VERSION` env, not `-Pversion`

Gradle project properties passed to the root build do **not** cross
`includeBuild` boundaries, so `-Pversion=X.Y.Z` would not reach the included
service builds. The convention plugins therefore read the version from an
environment variable:
`version = providers.environmentVariable("VERSION").orElse(providers.gradleProperty("version")).getOrElse("0.0.1-SNAPSHOT")`.
The workflow exports `VERSION=X.Y.Z` for the publish step. The hardcoded
`version` lines are removed from the root and all six service build files so the
plugin is the sole authority. _Alternative considered:_ writing `version` into
each `gradle.properties` — still multiple files and still not shared across
composite roots. Rejected.

### Semantic version derivation with git-cliff `--bumped-version`

git-cliff already parses conventional commits from `cliff.toml`. Using
`git cliff --bumped-version` yields the next version from commits since the last
tag (feat→minor, fix→patch, breaking→major), baseline `0.0.0` when no tag
exists. The `bump` choice defaults to `auto`; `auto` or an omitted value uses
git-cliff derivation, while `major`, `minor`, or `patch` computes that explicit
bump against the latest tag. This keeps one tool for both changelog and
versioning. _Alternative considered:_ semantic-release / release-please —
heavier, opinionated, and would duplicate git-cliff. Rejected.

### Manual backend release restricted to dev

The backend workflow is triggered only by `workflow_dispatch`, while its target
contract additionally requires release jobs to run only when
`github.ref == 'refs/heads/dev'`. `workflow_dispatch` alone does not enforce the
selected ref. The current workflow has no job-level ref guard, so this branch
restriction remains incomplete even though the workflow itself is manual.

### Two-layer changelog (git-cliff → opencode AI), mirroring opencode

Layer 1: `cliff.toml` is rewritten so `commit_parsers` map commit scope to
sections **Services / App / Other** and split feat vs fix into
`Improvements`/`Bugfixes`, emitting `` `hash` message `` lines. Layer 2: a
`.opencode/command/changelog.md` command embeds the git-cliff output via
`` !`...` `` and instructs the model to rewrite wording for readers, writing
`UPCOMING_CHANGELOG.md`. The workflow uses that file as the GitHub Release body.
_Alternative considered:_ git-cliff output alone (no AI) — rejected because the
user explicitly wants opencode-style edited prose.

### CI-scoped Anthropic-compatible opencode provider

A CI-only opencode config declares a custom provider with
`npm: @ai-sdk/anthropic`, `options.baseURL`, and `options.apiKey: "{env:...}"`,
keeping the existing `.opencode/opencode.json` (which holds dev `references`)
untouched. `opencode-ai` is pinned `>= 1.17.20` so `opencode run` returns a
non-zero exit code on failure; the job sets `timeout-minutes` to guard against
the known headless-hang issue.

### Image push: bootBuildImage local + docker push for two tags

`bootBuildImage` builds locally, then the workflow `docker login` to GHCR,
`docker tag`/`docker push` for both `X.Y.Z` and `latest`. Chosen over
`bootBuildImage --publishImage` because the latter publishes a single tag per
invocation, so `latest` would still require a separate push.

### Fail-closed ordering

Steps run strictly: resolve version → gate (all four services) → build+push
images → git-cliff → AI edit → tag + Release. Any failure before the final step
means no tag and no Release, so a failed run never leaves a half-published
version.

### API-derived SDK and runtime validation

The SDK is regenerated from the merged OpenAPI document rather than maintained
as a hand-written parallel contract. The generator's TypeScript, SDK, Zod, and
fetch plugins are the source of the public operation and schema exports. Zod
request validators run before transport and response validators run on returned
data, so contract violations fail at the SDK boundary. `zod` remains in
`dependencies`, not only development dependencies, because consumers execute the
validators.

### SDK CI drift and tarball smoke test

SDK CI first regenerates the service and merged OpenAPI documents, then
regenerates the SDK and fails if tracked generated files differ. Typecheck and
build catch declaration and distribution errors. The pack check installs the
actual tarball in a temporary project and exercises the public schema plus both
runtime validation failure paths. This verifies the artifact consumers receive,
not merely the source tree.

### Changesets and GitHub Packages release model

SDK release automation is intentionally separate from the manual backend
release. A push to `dev` invokes Changesets' action; pending changesets produce
or update a release PR, and the merged release PR invokes `release:sdk` to
version and publish `@smiskinext/smiski-ts` to restricted GitHub Packages. The
workflow already expresses this target distinction, but root package scripts,
the Changesets dependency, and a coherent workspace/standalone install model are
still required before the behavior is operational.

## Risks / Trade-offs

- **opencode exit-0-on-failure in old V1 builds** → Pin `opencode-ai >= 1.17.20`
  and additionally assert `UPCOMING_CHANGELOG.md` is non-empty before
  proceeding.
- **opencode headless hang after tool calls** → Bound the AI step with
  `timeout-minutes`.
- **Release re-runs the full `integrationTest` suite (Docker/Testcontainers)** →
  Slower releases, but correctness is prioritized per the accepted trade-off;
  `ubuntu-latest` provides Docker so no extra service containers are declared.
- **Removing hardcoded versions could surface a build that relied on the literal
  `0.0.1-SNAPSHOT`** → Convention-plugin fallback keeps the identical default;
  verified by building a service with and without `VERSION` set.
- **git-cliff scope→section mapping depends on disciplined conventional scopes**
  → Unscoped or unknown-scoped commits fall back to `Other`, so nothing is
  dropped.
- **AI edits could drift from facts** → The deterministic git-cliff output is
  the factual source; the command prompt constrains the model to rewrite wording
  only, not invent entries.
- **Workspace versus standalone SDK installation** → Root commands currently use
  `--ignore-workspace` while the SDK is listed as a workspace package. Do not
  describe release wiring as complete until one dependency model is chosen,
  lockfile behavior is consistent, and both CI and publishing pass from a clean
  checkout.
- **Changesets action cannot publish without root wiring** → The workflow calls
  `pnpm release:sdk`, but the root script and Changesets dependency are missing.
  Add and verify them before relying on automated SDK release PRs or publishing.
- **Manual dispatch can target a non-dev ref** → Add a job-level `dev` ref guard
  before version resolution so every downstream backend release job is skipped
  before any release action when the selected ref is not `dev`.

## Migration Plan

1. Update the two `build-logic` convention plugins to read `VERSION`.
2. Remove the hardcoded `version` lines from root + six service build files.
3. Verify default (`0.0.1-SNAPSHOT`) and injected (`VERSION=9.9.9`) builds.
4. Rewrite `cliff.toml` grouping and add GitHub remote settings as needed.
5. Add the CI opencode provider config and `.opencode/command/changelog.md`.
6. Add `.github/workflows/release.yml` wiring the fail-closed sequence.
7. Add and verify a job-level `dev` ref guard before backend version resolution.
8. Keep the verified SDK generation, schema export, runtime validation, and CI
   smoke checks aligned with the merged OpenAPI contract.
9. Resolve the SDK dependency model, add the Changesets dependency/scripts, make
   `release:sdk` work from a clean checkout, and verify release PR and GitHub
   Packages publishing end to end.

Rollback: the release workflow is additive and manual; disabling or deleting
`release.yml` restores prior behavior. The build-logic version change is safe to
revert independently since the fallback equals the previous literal.

## Open Questions

- The exact Anthropic-compatible provider `baseURL`, model id, and secret name
  are supplied by the maintainer at setup; the config references them via
  `{env:...}` placeholders until then.
- Should `sdks/typescript` remain a pnpm workspace package, or should SDK
  generation and publishing consistently use standalone `--ignore-workspace`
  installs? The answer must cover local commands, CI, lockfile behavior, and
  `release:sdk`.
- Which root Changesets scripts and package-manager invocation should be the
  supported release contract? Until this is decided and verified, SDK release
  wiring remains incomplete.
