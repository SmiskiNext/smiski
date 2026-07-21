## Context

The repository emits three per-service OpenAPI 3.1 documents:
`services/tenant/openapi.yaml`, `services/meet/openapi.yaml`, and
`services/record/openapi.yaml`. Each is generated from tests via
`generateOpenApiDocsFromTests` and committed to the repo. CI (`test.yml`
`openapi` job) and the pre-push hook already enforce a per-service drift check
plus Redocly lint. There is no combined document describing the full gateway
surface.

All three specs independently define components named `ProblemDetail` and
`Violation` (shared error contract). `@redocly/cli` (2.37.0) is already a dev
dependency. Redocly has no `merge` command; the officially documented command
for combining separate API descriptions is `join` (marked experimental).

## Goals / Non-Goals

**Goals:**

- Produce a single committed `services/openapi.yaml` combining the three
  per-service specs.
- Resolve duplicate component names deterministically.
- Give the combined document a fixed, gateway-oriented `info` block.
- Provide an npm script that regenerates the combined spec as part of the
  existing `pnpm run openapi` flow.
- Guard the committed combined spec against drift in both CI and pre-push,
  consistent with the existing per-service drift mechanism.

**Non-Goals:**

- Changing how per-service specs are generated or their content.
- Deploying, publishing, or serving the combined spec.
- Introducing a new API gateway runtime component.

## Decisions

### Decision: Use `redocly join` (not `merge`)

Redocly has no `merge` command. `join` is the documented command for combining
multiple separate API descriptions into one file; `bundle` only resolves `$ref`
within a single root document. Verified locally: `join` of the three specs
produces a valid combined document.

- Alternative considered: hand-rolled YAML concatenation — rejected as fragile
  and duplicating Redocly's conflict handling.

### Decision: Resolve component collisions with `--prefix-components-with-info-prop title`

Bare `join` aborts with `Conflict on components => schemas : ProblemDetail`.
Prefixing components by each source's `info.title` yields
`Tenant_ProblemDetail`, `Meet_ProblemDetail`, `Record_ProblemDetail`, etc., and
the combined document passes `redocly lint` with zero errors.

- Tags do not collide (each service uses distinct controller tags), so
  `x-tagGroups` is auto-generated per source title and left as-is.

### Decision: Set the combined `info` block via a two-step join then bundle

`join` always takes `info.title`/`version` from the first input file (would be
`Tenant`). To give the combined document its own identity, run `join` first,
then `bundle` the joined output through a Redocly config containing an
`info-override` decorator (title/version/description). Verified locally: the
final document reports the overridden `info` and remains valid.

- Alternative considered: overriding on each input before join — rejected as
  more steps and mutating per-service inputs indirectly.

### Decision: Commit `services/openapi.yaml` and enforce drift

Treat the combined spec as a committed generated artifact, matching the existing
per-service model. CI and pre-push regenerate it and fail on any diff, directing
the author to run `pnpm run openapi` and commit. This keeps the combined spec
reviewable in PR diffs.

### Decision: npm script shape

- `openapi:join`: runs `join` (+ `bundle`/override) to write
  `services/openapi.yaml`.
- `openapi:lint`: extended to also lint `services/openapi.yaml`.
- `openapi:generate` → `openapi:join` chained so `pnpm run openapi` produces and
  lints everything in one command.

### Decision: CI placement

Extend the existing `openapi` job in `test.yml` (already provisions toolchain +
pnpm cache) with merge + drift + lint steps rather than adding a separate job,
avoiding duplicated setup. The `join` operation is fast (~30ms) so running it
even when a subset of services changed is negligible.

## Risks / Trade-offs

- `join` is experimental → pinned `@redocly/cli` version already in the
  lockfile; drift check will surface any behavior change in the committed diff
  before merge.
- Combined spec runs in a per-service matrix job → the same merge may run
  multiple times when several services change. Mitigation: operation is
  near-instant and idempotent; drift result is identical across matrix entries.
- Prefixed component names (`Tenant_`, `Meet_`) are less clean than a truly
  unified schema → acceptable because per-service error contracts are identical
  in shape but conceptually independent; deduplication is out of scope.

## Migration Plan

1. Add scripts and Redocly override config.
2. Generate and commit initial `services/openapi.yaml`.
3. Add CI steps and pre-push hook check.
4. Update specs.

Rollback: remove the scripts/steps and delete `services/openapi.yaml`; no
runtime impact.
