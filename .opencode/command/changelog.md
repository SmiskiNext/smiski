---
model: anthropic/claude-sonnet-4-5
---

Create `UPCOMING_CHANGELOG.md` from the structured changelog input below. If
`UPCOMING_CHANGELOG.md` already exists, ignore its current contents completely.
Do not preserve, merge, or reuse text from the existing file.

The input is produced by git-cliff and already contains the exact commit range
since the last release. The commits are already filtered and grouped into the
release sections `Services`, `App`, and `Other`. Do not fetch GitHub releases,
pull requests, or build your own commit list. Never invent entries that are not
present in the input.

Rules:

- Write the final file with release sections in this order: `## Services`,
  `## App`, `## Other`.
- Only include a section that has at least one notable entry.
- Preserve each section's existing split: keep bug fixes under `### Bugfixes`
  and other notable entries under `### Improvements` when a section shows both.
- When a section lists entries without subheaders, keep them as a flat list.
- Omit empty sections and empty subsections.
- Keep one bullet per commit you keep.
- Skip commits that are entirely internal, CI, tests, refactors with no user
  impact, or otherwise not user-facing.
- Start each bullet with a capital letter.
- Rewrite wording so it reads clearly for users; describe what changed for them,
  not the internal code detail.
- Do not copy raw commit prefixes like `feat:` or `fix:` or trailing pull
  request numbers like `(#123)`.
- Do not include a contributor thank-you block.
- Focus on the fewest words that convey the change; users skim the changelog.
- If no notable entries remain, write exactly `No notable changes.`

<changelog_input>

!`git cliff --unreleased --tag "v$VERSION"`

</changelog_input>
