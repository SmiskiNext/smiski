# Context

The monorepo CI pipeline currently has four independent failure modes that block
routine validation and release operations: a broken manual git-cliff download in
release automation, Android secret decode failure without pre-checks, dependency
lockfile drift for web CI, and upcoming Node.js 20 deprecation across GitHub
Action dependencies. The repository already has complete workflow coverage and
does not need functional CI redesign; it needs reliability maintenance updates
that preserve existing behavior while removing known failure points.

## Goals / Non-Goals

**Goals:**

- Restore workflow reliability by removing the obsolete manual git-cliff install
  in release automation.
- Add deterministic early failure behavior in Android CI when
  `GOOGLE_SERVICES_JSON_BASE64` is missing.
- Re-align `pnpm-lock.yaml` with current web dependencies so lockfile checks
  pass.
- Upgrade all action references in workflow files from Node.js 20-based majors
  to supported newer majors.
- Keep existing workflow triggers, jobs, and sequencing intact.

**Non-Goals:**

- Repair or rotate GitHub repository secrets.
- Add new jobs, matrix strategies, or workflow features.
- Modify application source code outside CI workflow files and lockfile
  regeneration.
- Rework release or build logic beyond the targeted fixes.

## Decisions

1. Remove redundant git-cliff bootstrap step from `release.yml`.
    - Rationale: The release workflow already uses `orhun/git-cliff-action@v4`,
      which installs and executes the correct binary internally. Keeping a
      separate manual install creates an external URL dependency that has
      already broken.
    - Alternative considered: Update the manual URL to the new naming
      convention. Rejected because it duplicates capability already handled by
      the action and increases future maintenance burden.

2. Add a precondition guard before Android base64 decoding.
    - Rationale: Failing fast on an empty secret yields a clear, actionable
      error instead of a low-signal `base64: invalid input` failure. This
      improves operability without changing artifact generation flow.
    - Alternative considered: Replace decode with conditional skip logic.
      Rejected because Android build requires the file and skipping would hide
      misconfiguration.

3. Regenerate lockfile with `pnpm install` and validate with
   `pnpm install --frozen-lockfile`.
    - Rationale: The lockfile is explicitly stale versus
      `frontends/web/package.json`; regeneration is the canonical way to produce
      deterministic dependency state used by CI.
    - Alternative considered: Manually edit lockfile entries. Rejected due to
      high error probability and non-deterministic outcomes.

4. Upgrade action majors consistently across all workflow files.
    - Rationale: Node.js 20 runtime deprecation affects actions pinned to older
      majors. A coordinated bump prevents piecemeal failures and keeps CI
      supported.
    - Alternative considered: Delay upgrades until deprecation date. Rejected
      because existing warnings become failures and create avoidable disruption.

## Risks / Trade-offs

- Action major upgrades can introduce behavioral defaults changes → Mitigation:
  Limit updates to explicitly requested action families and keep workflow logic
  unchanged.
- Android guard may cause immediate hard failures in repos with currently unset
  secrets → Mitigation: Fail message explicitly points to secret configuration
  as the required manual remediation.
- Lockfile regeneration can update more transitive metadata than expected →
  Mitigation: Validate install determinism with `pnpm install --frozen-lockfile`
  after regeneration.
- Removing manual git-cliff install could expose hidden dependency on that step
  → Mitigation: Release job continues to use `orhun/git-cliff-action@v4`, which
  is already responsible for changelog generation.

## Migration Plan

1. Update all five workflow files with the requested action version upgrades.
2. Remove the manual git-cliff curl/tar step in `release.yml`.
3. Add an explicit non-empty secret validation step before base64 decode in
   `android.yml`.
4. Run `pnpm install` at repo root to regenerate `pnpm-lock.yaml`.
5. Run `pnpm install --frozen-lockfile` to verify lockfile consistency.
6. Open PR and let CI re-run all workflows.

Rollback strategy:

- Revert the workflow and lockfile commit if regressions appear.
- If needed, selectively revert a specific action upgrade while preserving other
  validated fixes.

## Open Questions

- None for artifact creation. Repository secret correction remains an external
  manual operation and is intentionally out of scope for this change.
