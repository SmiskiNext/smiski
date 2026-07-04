# Tasks

## 1. Update workflow action runtimes

- [x] 1.1 Inventory action usages across all files in
      `/home/PNguyen/Workspace/MyProject/zero-meeting-system/.github/workflows/`
      and map required version upgrades
- [x] 1.2 Upgrade `actions/checkout`, `actions/setup-node`, `pnpm/action-setup`,
      `actions/setup-java`, `gradle/actions/setup-gradle`, and
      `actions/upload-artifact` to requested majors in every applicable workflow
      file
- [x] 1.3 Verify no targeted deprecated majors remain across the five workflow
      files ← (verify: all requested action families resolve to the new majors
      and no workflow is missed)

## 2. Fix release and Android workflow reliability

- [x] 2.1 Remove the manual git-cliff `curl | tar` installation step from
      `/home/PNguyen/Workspace/MyProject/zero-meeting-system/.github/workflows/release.yml`
- [x] 2.2 Add a non-empty `GOOGLE_SERVICES_JSON_BASE64` validation guard before
      decode in
      `/home/PNguyen/Workspace/MyProject/zero-meeting-system/.github/workflows/android.yml`
- [x] 2.3 Confirm release changelog generation still depends on
      `orhun/git-cliff-action@v4` and Android decode behavior is unchanged when
      secret is present ← (verify: release no longer depends on external
      git-cliff URL and Android fails fast only on missing secret)

## 3. Regenerate and validate lockfile state

- [x] 3.1 Run `pnpm install` at repository root to regenerate
      `/home/PNguyen/Workspace/MyProject/zero-meeting-system/pnpm-lock.yaml`
- [x] 3.2 Run `pnpm install --frozen-lockfile` to validate deterministic install
      with the regenerated lockfile
- [x] 3.3 Review lockfile diff scope for expected dependency synchronization
      with `frontends/web/package.json` ← (verify: frozen-lockfile check passes
      and lockfile changes correspond to manifest drift only)

## 4. Final verification and readiness

- [x] 4.1 Validate workflow YAML syntax and formatting consistency after edits
- [x] 4.2 Summarize out-of-scope manual action for repository secret correction
      in change notes
- [x] 4.3 Confirm proposal/design/spec/task artifacts are complete and aligned
      for implementation handoff ← (verify: OpenSpec status shows required
      artifacts done and scope boundaries are explicit)
