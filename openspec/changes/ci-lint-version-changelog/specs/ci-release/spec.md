# ADDED Requirements

## Requirement: Release workflow triggers on push to main

The system SHALL run a `release` GitHub Actions workflow on every push to the
`main` branch. The workflow SHALL have write permissions for `contents` (to
commit files and create tags) and SHALL use `GITHUB_TOKEN` for authentication.

### Scenario: Merge commit lands on main

- **WHEN** a pull request is merged into `main`
- **THEN** the `release.yml` workflow is triggered automatically

### Scenario: Direct push to main

- **WHEN** a commit is pushed directly to `main` (e.g., a bot release commit)
- **THEN** the `release.yml` workflow is triggered; if the commit message
  contains `[skip ci]`, GitHub Actions SHALL skip the workflow

## Requirement: CHANGELOG.md is generated via git-cliff

The system SHALL use git-cliff with `cliff.toml` at the repository root to
generate or update `CHANGELOG.md` on every release run. The changelog SHALL
group commits by type (feat, fix, perf, refactor) and SHALL exclude `chore`,
`ci`, `docs`, and `style` commits from release notes. The cliff output SHALL
cover all commits since the previous git tag.

### Scenario: New features and fixes since last tag

- **WHEN** there are commits with types `feat` or `fix` since the latest git tag
- **THEN** git-cliff produces a `CHANGELOG.md` with a new section for the
  upcoming version listing those commits under their respective headings

### Scenario: Only chore commits since last tag

- **WHEN** all commits since the latest git tag are of type `chore` or `ci`
- **THEN** git-cliff produces a changelog section with no user-facing entries;
  the release workflow MUST still proceed and produce a patch bump

### Scenario: No previous git tag exists

- **WHEN** there are no existing git tags in the repository
- **THEN** git-cliff generates the full changelog from the repository's entire
  commit history

## Requirement: Semver version is bumped based on conventional commits

The system SHALL analyze conventional commit types since the previous git tag to
determine the semver component to increment in root `package.json`:

- At least one commit with a `BREAKING CHANGE` footer or a `!` after the type
  (e.g., `feat!:`) → major bump
- At least one `feat:` commit (and no breaking changes) → minor bump
- All other cases (only `fix`, `perf`, `refactor`, `chore`, etc.) → patch bump

The bumped version SHALL be written back to the `version` field in root
`package.json`.

### Scenario: PR contains a breaking-change commit

- **WHEN** at least one commit since the last tag includes `BREAKING CHANGE:` in
  its footer or uses the `!` convention
- **THEN** the major version component is incremented and minor and patch are
  reset to 0 (e.g., 1.2.3 → 2.0.0)

### Scenario: PR adds a new feature

- **WHEN** at least one `feat:` commit exists since the last tag and no breaking
  changes are present
- **THEN** the minor version component is incremented and patch is reset to 0
  (e.g., 1.2.3 → 1.3.0)

### Scenario: PR contains only fixes

- **WHEN** all commits since the last tag are of type `fix`, `perf`, or other
  non-feature non-breaking types
- **THEN** the patch version component is incremented (e.g., 1.2.3 → 1.2.4)

## Requirement: Release commit, git tag, and GitHub Release are created

After bumping the version and generating the changelog, the system SHALL:

1. Commit `CHANGELOG.md` and `package.json` to `main` with the message
   `chore(release): vX.Y.Z [skip ci]` attributed to the GitHub Actions bot
2. Create a git tag `vX.Y.Z` pointing to that commit
3. Create a GitHub Release named `vX.Y.Z` with the changelog section for that
   version as the release body

### Scenario: Version bump and changelog are committed

- **WHEN** the release workflow completes the version bump and changelog
  generation
- **THEN** a commit appears on `main` with message
  `chore(release): vX.Y.Z [skip ci]` containing only changes to `CHANGELOG.md`
  and `package.json`

### Scenario: Git tag is created

- **WHEN** the release commit is pushed to `main`
- **THEN** a git tag `vX.Y.Z` exists in the repository pointing to that commit

### Scenario: GitHub Release is published

- **WHEN** the git tag is created
- **THEN** a GitHub Release named `vX.Y.Z` is visible on the repository releases
  page with the changelog diff for that version as the body

### Scenario: Release commit does not trigger a second release run

- **WHEN** the release workflow pushes `chore(release): vX.Y.Z [skip ci]`
- **THEN** the `[skip ci]` marker prevents GitHub Actions from triggering a new
  `release.yml` run

## Requirement: cliff.toml configures changelog format

The system SHALL include a `cliff.toml` file at the repository root that
configures git-cliff to parse conventional commits and produce a Markdown
changelog with sections: Features, Bug Fixes, Performance, and Other Changes.
Commits of type `chore`, `ci`, `docs`, and `style` SHALL be excluded from the
changelog body.

### Scenario: cliff.toml is present at repository root

- **WHEN** git-cliff is invoked without an explicit `--config` flag
- **THEN** it reads `cliff.toml` from the current directory (repository root)
  and applies its settings

### Scenario: Feature commit appears under Features heading

- **WHEN** a commit has type `feat`
- **THEN** it appears under the "Features" heading in the generated CHANGELOG.md

### Scenario: Fix commit appears under Bug Fixes heading

- **WHEN** a commit has type `fix`
- **THEN** it appears under the "Bug Fixes" heading in the generated
  CHANGELOG.md

### Scenario: Chore commit is excluded from changelog body

- **WHEN** a commit has type `chore` or `ci`
- **THEN** it does not appear in any section of the generated CHANGELOG.md

## Requirement: .actrc enables local CI testing with act

The system SHALL include an `.actrc` file at the repository root that configures
nektos/act with a medium runner image and linux/amd64 architecture, enabling
developers to run `act pull_request` locally to validate workflows before
pushing.

### Scenario: Developer runs act locally

- **WHEN** a developer runs `act pull_request` from the repository root
- **THEN** act reads `.actrc`, pulls the configured runner image, and executes
  the lint workflow steps locally
