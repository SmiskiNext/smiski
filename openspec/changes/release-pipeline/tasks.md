# Tasks: release-pipeline

## 1. Single version source of truth

- [x] 1.1 Update
      `build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.base.gradle.kts`
      to set `version` from the `VERSION` environment variable, falling back to
      gradle property `version`, then to `0.0.1-SNAPSHOT`
- [x] 1.2 Apply the same version resolution to
      `build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.mongodb.base.gradle.kts`
- [x] 1.3 Remove `version = "0.0.1-SNAPSHOT"` from root `build.gradle.kts`
- [x] 1.4 Remove `version = "0.0.1-SNAPSHOT"` from
      `services/{tenant,meet,record,notification,shared,proto}/build.gradle.kts`
- [x] 1.5 Verify default build resolves `0.0.1-SNAPSHOT` and
      `VERSION=9.9.9 ./services/gradlew -p services/tenant properties` resolves
      `9.9.9` for every included service build ← (verify: no hardcoded version
      remains in root or any service build.gradle.kts; env override propagates
      across all composite builds; default fallback unchanged)

## 2. Changelog: deterministic layer (git-cliff)

- [x] 2.1 Rewrite `cliff.toml` `commit_parsers` so commit scope maps to
      top-level sections Services (`tenant`/`meet`/`record`/`notification`), App
      (`app`), and Other (everything else, including unscoped), preserving
      section order Services → App → Other
- [x] 2.2 Update the `cliff.toml` body template to split `Improvements`
      (non-fix) and `Bugfixes` (fix) within a section, listing entries directly
      when only improvements exist, and rendering each entry as
      ``- `<short-hash>` <message>`` with no contributor thank-you block
- [x] 2.3 Confirm `git cliff --bumped-version` derives the next version from
      conventional commits with baseline `0.0.0` when no tag exists ← (verify:
      sections render as Services/App/Other from real repo commits; feat/fix
      split correct; bumped-version output is a valid X.Y.Z)

## 3. Changelog: AI editing layer (opencode)

- [x] 3.1 Add a CI-scoped opencode config declaring an Anthropic-compatible
      provider (`npm: @ai-sdk/anthropic`, `options.baseURL`,
      `options.apiKey: "{env:...}"`) without modifying the existing
      `.opencode/opencode.json`
- [x] 3.2 Create `.opencode/command/changelog.md` with frontmatter `model:`
      pointing at the CI provider, a prompt that rewrites wording for readers
      while preserving the Services/App/Other structure (capitalize entries,
      strip `feat:`/`fix:` prefixes, drop non-user-facing commits), and embeds
      the git-cliff output via `` !`...` ``, writing the result to
      `UPCOMING_CHANGELOG.md` ← (verify: prompt preserves structure and never
      invents entries; output path is UPCOMING_CHANGELOG.md; no secret value
      embedded)

## 4. Release workflow

- [x] 4.1 Create `.github/workflows/release.yml` triggered only by
      `workflow_dispatch` on `dev` with an optional `bump` choice input
      (major/minor/patch); set least-privilege `contents: write` +
      `packages: write`, concurrency group without `cancel-in-progress`, and
      SHA-pinned actions
- [x] 4.2 Add a version-resolution step: derive via
      `git cliff --bumped-version`, override with `bump` input when provided,
      expose resolved `X.Y.Z` as a job output
- [x] 4.3 Add the quality-gate job running
      `./services/gradlew -p services/<name> assemble test integrationTest` for
      all four services on a Docker-enabled runner, using `.mise.toml` toolchain
      provisioning
- [x] 4.4 Add the publish step: export `VERSION=X.Y.Z`, run `bootBuildImage` per
      service, `docker login` GHCR, then `docker tag`/`docker push` each
      `ghcr.io/smiskinext/<service>` with `X.Y.Z` and `latest` ← (verify: all
      four images pushed with both tags and identical version; gate must pass
      first)
- [x] 4.5 Add the changelog step: install pinned `opencode-ai >= 1.17.20`, run
      `opencode run --command changelog` with the AI API key from a GitHub
      secret via env, bound by `timeout-minutes`, and fail the workflow if the
      step errors or `UPCOMING_CHANGELOG.md` is empty
- [x] 4.6 Add the final tag + release step: create and push git tag `vX.Y.Z` on
      the dispatched commit and create a GitHub Release using
      `UPCOMING_CHANGELOG.md` as the body, only after gate, publish, and
      changelog all succeed ← (verify: fail-closed ordering — no tag/release on
      any earlier failure; tag points at dispatched commit; release body is the
      edited notes)

## 5. Verification and hygiene

- [x] 5.1 Run `pnpm format` and `pnpm lint` over new/changed yaml/md/toml files
- [x] 5.2 Confirm `UPCOMING_CHANGELOG.md` is not committed (gitignored or never
      added) and that the CI provider config contains no hardcoded secret ←
      (verify: workflow YAML is valid; all non-GitHub actions SHA-pinned; secret
      only via `{env:...}`; no CHANGELOG.md committed)
