# Tasks

## 1. Merge tooling and scripts

- [x] 1.1 Add a Redocly config for the combined spec `info-override` decorator
      (title, version, description) for the gateway surface
- [x] 1.2 Add `openapi:join` npm script that runs `redocly join` on the three
      per-service specs with `--prefix-components-with-info-prop title`, then
      applies the `info-override` via `redocly bundle` to write
      `services/openapi.yaml`
- [x] 1.3 Extend `openapi:lint` to also lint `services/openapi.yaml`
- [x] 1.4 Chain `openapi:join` into the `openapi:generate`/`openapi` flow so
      `pnpm run openapi` produces and lints the combined document ← (verify:
      `pnpm run openapi` regenerates all specs + combined and lint passes with 0
      errors)

## 2. Committed artifact

- [x] 2.1 Generate and commit the initial `services/openapi.yaml`
- [x] 2.2 Confirm the combined `info` block matches the fixed values and
      components are prefixed (`Tenant_`, `Meet_`, `Record_`) ← (verify:
      services/openapi.yaml has fixed info.title and prefixed components,
      matches openapi-merge spec)

## 3. CI integration

- [x] 3.1 Extend the `openapi` job in `.github/workflows/test.yml` to regenerate
      the combined spec, drift-check it against the committed
      `services/openapi.yaml`, and lint it
- [x] 3.2 Ensure the drift failure message instructs running `pnpm run openapi`
      and committing the result ← (verify: CI steps match ci-pipeline delta
      scenarios; drift + lint gate the openapi job)

## 4. Pre-push hook

- [x] 4.1 Add a combined-spec drift check to `pre-push` in `lefthook.yml`,
      scoped to `services/{tenant,meet,record}/**`, restoring the working tree
      after checking ← (verify: hook matches git-hooks delta scenarios; skips
      when no relevant service changed, blocks on drift, leaves no uncommitted
      changes)

## 5. Verification

- [x] 5.1 Run `pnpm run openapi` and confirm no drift and clean lint
- [x] 5.2 Run `openspec validate add-openapi-merge --strict`
