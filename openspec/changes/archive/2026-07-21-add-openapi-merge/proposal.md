## Why

The monorepo emits three separate per-service OpenAPI documents (`tenant`,
`meet`, `record`), but consumers of the API gateway need a single unified
description of the whole surface. There is currently no combined spec, no script
to produce one, and no automated guard that keeps a combined spec in sync with
its per-service sources.

## What Changes

- Add a `redocly join` based merge that combines the three per-service specs
  into a single `services/openapi.yaml`, resolving the duplicated
  `ProblemDetail` and `Violation` component names by prefixing components with
  each source's `info.title` (`Tenant_`, `Meet_`, `Record_`).
- Apply a fixed `info` block (title/version/description) to the combined spec
  via a Redocly `info-override` decorator so the merged document identifies the
  gateway surface rather than inheriting the first input file's title.
- Add npm scripts: an `openapi:join` script that produces
  `services/openapi.yaml`, chained into the existing `openapi` flow; the
  existing `openapi:lint` extended to lint the combined spec as well.
- Commit `services/openapi.yaml` to the repository as a generated artifact.
- Extend the CI `openapi` job so it regenerates the combined spec, fails on
  drift against the committed `services/openapi.yaml`, and lints the combined
  spec.
- Extend the pre-push git hook to fail when the committed combined spec drifts
  from the regenerated output.

## Capabilities

### New Capabilities

- `openapi-merge`: Combining the per-service OpenAPI documents into a single
  committed `services/openapi.yaml`, including component-name conflict
  resolution, the fixed combined `info` block, the npm merge script, and the
  commit-plus-drift contract shared by CI and git hooks.

### Modified Capabilities

- `ci-pipeline`: The OpenAPI drift check requirement is extended to also
  regenerate, drift-check, and lint the combined `services/openapi.yaml`.
- `git-hooks`: The pre-push hook is extended to drift-check the combined
  `services/openapi.yaml`.

## Impact

- `package.json`: new/updated `openapi:join`, `openapi:lint`, `openapi` scripts.
- New Redocly config for the combined spec `info-override` decorator.
- `services/openapi.yaml`: new committed generated artifact.
- `.github/workflows/test.yml`: `openapi` job gains merge + drift + lint steps.
- `lefthook.yml`: `pre-push` gains a combined-spec drift check.
- Depends on `@redocly/cli` (already a dev dependency) `join` (experimental) and
  `bundle` commands.
