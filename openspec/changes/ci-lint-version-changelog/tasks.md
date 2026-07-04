# Tasks

## 1. Repository Scaffolding

- [x] 1.1 Create `.github/workflows/` directory at repository root
- [x] 1.2 Update `.gitignore` — append `.env` and `.secrets/` entries
- [x] 1.3 Add `.actrc` at repository root with medium runner image and
      linux/amd64 architecture ← (verify: `act pull_request` reads the file and
      selects the correct image without flags)

## 2. Lint Workflow

- [x] 2.1 Create `.github/workflows/lint.yml` with trigger
      `pull_request: branches: [main]`
- [x] 2.2 Add `services-format` job: setup JDK 25,
      `gradle/actions/setup-gradle@v4`, run `./services/gradlew spotlessCheck`
- [x] 2.3 Add `proto-format` job: setup JDK 25,
      `gradle/actions/setup-gradle@v4`, run
      `./services/gradlew -p services/proto bufFormatCheck`
- [x] 2.4 Add `web-check` job: setup Node LTS, `pnpm/action-setup`,
      `pnpm install`, run `pnpm --dir frontends/web biome check`
- [x] 2.5 Add `general-format` job: setup Node LTS, `pnpm/action-setup`,
      `pnpm install`, run Prettier check on `**/*.{md,json,toml,yaml,yml,sh}`
      excluding `frontends/web/` and `node_modules/`
- [x] 2.6 Add `commitlint` job: fetch full PR commit range (`base.sha..HEAD`),
      run commitlint across all commits using `commitlint.config.js`
- [x] 2.7 Confirm all six jobs are listed as parallel (no `needs:` dependencies
      between them) ← (verify: actionlint reports no errors; a test PR triggers
      all jobs simultaneously in the Actions tab)

## 3. Backend Test Workflow

- [x] 3.1 Create `.github/workflows/backend-test.yml` with trigger
      `pull_request: branches: [main]` and path filter `services/**`
- [x] 3.2 Add single job: setup JDK 25, `gradle/actions/setup-gradle@v4`, run
      `./services/gradlew test` ← (verify: a PR touching only `frontends/web/`
      does not trigger this workflow; a PR touching `services/` does)

## 4. Web Test Workflow

- [x] 4.1 Create `.github/workflows/web-test.yml` with trigger
      `pull_request: branches: [main]` and path filter `frontends/web/**`
- [x] 4.2 Add single job: setup Node LTS, `pnpm/action-setup`,
      `pnpm install --frozen-lockfile`, run `pnpm --dir frontends/web test` ←
      (verify: a PR touching only `services/` does not trigger this workflow; a
      PR touching `frontends/web/` does)

## 5. Android Workflow

- [x] 5.1 Create `.github/workflows/android.yml` with trigger
      `pull_request: branches: [main]` and path filter
      `frontends/android-app/**`
- [x] 5.2 Add job setup: JDK 21 (matching Android toolchain), Android SDK,
      `gradle/actions/setup-gradle@v4`
- [x] 5.3 Add step: decode `GOOGLE_SERVICES_JSON_BASE64` secret into
      `frontends/android-app/app/google-services.json`
- [x] 5.4 Add step: run
      `./frontends/android-app/gradlew -p frontends/android-app :app:spotlessCheck`
- [x] 5.5 Add step: run
      `./frontends/android-app/gradlew -p frontends/android-app :app:testDebugUnitTest`
- [x] 5.6 Add step: run
      `./frontends/android-app/gradlew -p frontends/android-app :app:assembleDebug`
- [x] 5.7 Add step: upload APK artifact using `actions/upload-artifact@v4` ←
      (verify: a passing PR produces a downloadable APK artifact in the Actions
      run summary; missing secret causes decode step to fail before any Gradle
      task)

## 6. git-cliff Configuration

- [x] 6.1 Add `cliff.toml` at repository root
- [x] 6.2 Configure commit parsers: `feat` → Features, `fix` → Bug Fixes, `perf`
      → Performance, `refactor` → Other Changes
- [x] 6.3 Configure filters to exclude commit types `chore`, `ci`, `docs`,
      `style` from changelog body
- [x] 6.4 Configure tag pattern `v[0-9]*` so git-cliff detects existing tags
      correctly ← (verify: running `git-cliff --unreleased` locally produces a
      CHANGELOG.md section with correct groupings and excludes chore/ci commits)

## 7. Release Workflow

- [x] 7.1 Create `.github/workflows/release.yml` with trigger
      `push: branches: [main]` and `contents: write` permission
- [x] 7.2 Add step: install git-cliff (via cargo or pre-built binary)
- [x] 7.3 Add step: determine version bump type by scanning commits since
      previous tag (breaking → major, feat → minor, else → patch)
- [x] 7.4 Add step: compute new semver string and update `version` field in root
      `package.json`
- [x] 7.5 Add step: run `git-cliff --tag vX.Y.Z -o CHANGELOG.md` to regenerate
      full changelog with the new tag
- [x] 7.6 Add step: configure git bot identity, commit `CHANGELOG.md` and
      `package.json` with message `chore(release): vX.Y.Z [skip ci]`
- [x] 7.7 Add step: push the release commit and create git tag `vX.Y.Z`
- [x] 7.8 Add step: create GitHub Release using `gh release create vX.Y.Z` with
      the new changelog section as the body ← (verify: merging a `feat:` PR to
      main creates a minor bump tag, a GitHub Release with changelog body, and a
      release commit with `[skip ci]` that does not retrigger the workflow)

## 8. Workflow Validation

- [x] 8.1 Install actionlint locally and run against all five workflow files;
      fix any reported issues
- [x] 8.2 Run `act pull_request --dry-run` from repository root to validate
      workflow parsing via nektos/act ← (verify: actionlint exits 0 with no
      errors; act dry-run lists correct jobs for each workflow without syntax
      errors)
