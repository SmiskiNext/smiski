## Context

The repo has four Spring Boot microservices under `services/` wired as Gradle
**composite builds** (`includeBuild` in `services/settings.gradle.kts`), each
applying convention plugins from `build-logic/`. Every service and the root
`build.gradle.kts` hardcode `version = "0.0.1-SNAPSHOT"` (nine sites total).
Service images are configured via Spring Boot `bootBuildImage` in the
`service.base` and `service.mongodb.base` convention plugins, targeting
`ghcr.io/smiskinext/${project.name}:${project.version}`. Existing CI
(`build/lint/test/security.yml`) is validation-only and hardened (SHA-pinned
actions, `.mise.toml` toolchain, least-privilege, concurrency). A `cliff.toml`
(git-cliff) exists with conventional-commit parsing and
`tag_pattern = "v[0-9]*"`, but git-cliff is neither installed via `.mise.toml`
nor invoked anywhere; there is no `CHANGELOG.md` and no git tags. Commit
messages are enforced as conventional by commitlint. The repo already uses
opencode (`.opencode/opencode.json`, `@opencode-ai/plugin` 1.17.18). The default
branch is `dev`; there is no `main`.

## Goals / Non-Goals

**Goals:**

- One repeatable, manually-triggered release path that produces versioned,
  published service images plus a git tag and GitHub Release.
- A single version source of truth compatible with composite builds.
- Semantic version derivation from conventional commits, overridable by a manual
  bump input.
- An opencode-style two-layer changelog: deterministic structure (git-cliff)
  then AI wording (opencode run).

**Non-Goals:**

- Fixing k8s manifest image references (`ghcr.io/phunguy65/zms/...`).
- Auto-release on push or fully automatic (no-human) releases.
- Committing a `CHANGELOG.md` into the repo.
- Contributor thank-you blocks.
- Changing the `package.json` version or the existing validation workflows.

## Decisions

### Version propagation via `VERSION` env, not `-Pversion`

Gradle project properties passed to the root build do **not** cross
`includeBuild` boundaries, so `-Pversion=X.Y.Z` would not reach the included
service builds. The convention plugins therefore read the version from an
environment variable:
`version = providers.environmentVariable("VERSION").orElse(providers.gradleProperty("version")).getOrElse("0.0.1-SNAPSHOT")`.
The workflow exports `VERSION=X.Y.Z` for the publish step. The hardcoded
`version` lines are removed from the root and all seven service build files so
the plugin is the sole authority. _Alternative considered:_ writing `version`
into each `gradle.properties` — still multiple files and still not shared across
composite roots. Rejected.

### Semantic version derivation with git-cliff `--bumped-version`

git-cliff already parses conventional commits from `cliff.toml`. Using
`git cliff --bumped-version` yields the next version from commits since the last
tag (feat→minor, fix→patch, breaking→major), baseline `0.0.0` when no tag
exists. The optional `bump` input, when set, overrides by computing the chosen
bump against the latest tag. This keeps one tool for both changelog and
versioning. _Alternative considered:_ semantic-release / release-please —
heavier, opinionated, and would duplicate git-cliff. Rejected.

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

## Migration Plan

1. Update the two `build-logic` convention plugins to read `VERSION`.
2. Remove the hardcoded `version` lines from root + seven service build files.
3. Verify default (`0.0.1-SNAPSHOT`) and injected (`VERSION=9.9.9`) builds.
4. Rewrite `cliff.toml` grouping and add GitHub remote settings as needed.
5. Add the CI opencode provider config and `.opencode/command/changelog.md`.
6. Add `.github/workflows/release.yml` wiring the fail-closed sequence.

Rollback: the release workflow is additive and manual; disabling or deleting
`release.yml` restores prior behavior. The build-logic version change is safe to
revert independently since the fallback equals the previous literal.

## Open Questions

- The exact Anthropic-compatible provider `baseURL`, model id, and secret name
  are supplied by the maintainer at setup; the config references them via
  `{env:...}` placeholders until then.
