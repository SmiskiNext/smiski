## [2026-04-26] Round 1 (from apply auto-verify)

### Verifier

- Fixed: TypeScript error in `account-settings-form.tsx` — `onSubmit` local
  variable shadowed the form's `onSubmit` handler, causing incorrect function
  call. Renamed to `onFormSubmit` and updated `<form onSubmit={onFormSubmit}>`.

- Fixed: TypeScript error in `use-user-account-settings.ts` — duplicate
  `getCookieValue` function definition (static function AND inline copy inside
  callback). Removed duplicate; kept single module-level `getCookieValue`.

- Fixed: TypeScript error in `use-user-account-settings.test.ts` —
  `beforeEach`/`afterEach` not in scope. Added explicit imports from `vitest`.

- Fixed: Lint errors — unused `watchedValues` variable in form component
  (removed), unused `watch` import from `react-hook-form` (removed), unused
  `beforeEach`/`vi` imports in test file (removed), unused `errorFallback`
  parameter in logout callback (prefixed with `_`), non-null assertion
  `state.profile.createdAt!` replaced with `as string` cast.

- Fixed: `getCookieValue` cookie parsing bug — `match?.split('=')[1]` truncated
  values containing `=` characters. Changed to
  `match.split('=').slice(1).join('=')`.

- Fixed: Missing i18n translations — added `workspace.accountSettings` key
  namespace to both `en.json` and `vi.json` with all required labels, validation
  strings, destructive action copy, loading/error states.

### Notes

- 4 remaining lint errors are in `upcoming-meeting-card.tsx` — pre-existing from
  the `web-upcoming-host-meetings` feature, not introduced by this change.
- E2E tests (task 8.3) deferred — no E2E test suite was found in the project.
  Unit/component tests provide coverage.
- Avatar file upload: no upload endpoint exists in the generated SDK.
  Implemented display-only avatar with local `URL.createObjectURL()` preview and
  documented the limitation in `avatarHint` i18n key.
- Logout API: `logout()` requires `refreshToken` in request body; retrieved via
  `document.cookie` in the client hook context.
