---
name: gh-pr-workflow
description:
    Prepare a GitHub pull request from a feature branch — branch hygiene, commit
    shape, title/body, verification notes, screenshots for UI work, and replies
    to review comments.
---

# GitHub Pull Request Workflow

Ship a PR a reviewer can land without follow-up clarifying questions. The aim is
high signal in the title and body, evidence the change works, and clean replies
when feedback comes in.

## When to use

- You are about to open a PR for a change that is functionally complete.
- A reviewer left comments and you need to respond and push fixes.
- A PR has been open more than a day and needs to be brought back into shape
  (stale conflicts, missing description, missing verification).

## When not to use

- The change is not yet functionally complete. Finish the work first; draft PRs
  that bounce on review are noise.
- The repository uses a non-GitHub forge. Adjust to that forge's conventions; do
  not force GitHub-isms.

## Branch hygiene before opening

- Rebase or merge from the target base so the diff is current.
- Squash WIP commits into reviewable units. Prefer one commit per logical
  change; do not force one-commit-per-PR if the work is genuinely multi-step.
- Confirm tests, typecheck, and lint pass locally. Note any deliberate skips in
  the PR body.
- Remove debug prints, commented-out code, and `TODO` markers that are not
  tracked.

## PR title

Follow Conventional Commits: `type(scope): description`.

- Allowed types: `feat`, `refactor`, `chore`, `ci`, `fix`.
- Scope is the main area touched:
    - `services` — a backend service; name it when specific, e.g.
      `services/meet`.
    - `app` — the Forge app.
    - `docs` — documentation.
- Imperative mood, under 70 characters, no trailing period.
- Lead with the user-visible change, not the file touched.
  `feat(services/meet): allow CSV export from reports` beats
  `chore(app): update reports.tsx`.
- If the repo uses an issue prefix convention (`PAP-1234:`, `[security]`),
  follow it after the type.

## PR body

Use this structure:

```md
## What

- What changed and why. Prose is fine; add a table for benchmark or before/after
  numbers.

## How

- Per-file or per-area implementation details: the mechanism, not just a file
  list.
- Trade-offs, dropped alternatives, gotchas, migration or config implications.

## Scope

- What stays unchanged and what is explicitly out of scope.
- What this deliberately does not add.

## Testing

- The exact commands or steps you ran and their results.
- Screenshots or short clips for UI changes (required if pixels moved).
- Edge cases you exercised by hand.

## Flow

- A mermaid diagram when control or data flow is non-obvious.
```

Skip the `Flow` section for changes with no meaningful control or data flow
(typos, docs, config).

## Testing evidence

- Tests passing in CI is necessary, not sufficient. Reviewers also need to know
  the change behaves correctly end to end.
- For UI work, include screenshots of the golden path and one edge case. Tag
  dark and light mode if the project supports both.
- For migrations, include a dry-run plan and reversal steps.
- For performance changes, include a before/after measurement, not adjectives.

## Replying to review comments

- Reply on every comment, even with just "fixed in <commit-sha>" — silent fixes
  leave the reviewer guessing.
- Push fixes as new commits while review is active; do not amend during review
  unless the reviewer agrees.
- If you disagree with feedback, say so with one sentence of rationale and let
  the reviewer decide. Don't escalate over comments.
- Re-request review explicitly after pushing changes.

## Merge checklist

- All required checks green.
- All review comments resolved.
- PR title/body still accurate (update if scope changed mid-review).
- Linked issue moves to `in_review` or `done` per project convention.
- Delete the branch after merge unless it is a long-lived integration branch.

## Anti-patterns

- PR description that says "see commits". Reviewers should not need to read the
  log.
- Mixing refactor and behavior change in the same PR with no separation in the
  body.
- "Address feedback" commits that bundle unrelated edits. One commit per round
  of feedback is fine; one commit for everything in flight is not.
- Force-pushing during active review without telling the reviewer.
