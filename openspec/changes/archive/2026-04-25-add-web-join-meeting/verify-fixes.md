## [2026-04-25] Round 2 (from verify-fix loop)

### Verifier

- Fixed (WARNING 1 — HIGH): Added `joinMeeting.validation.required` i18n key to
  both en.json and vi.json with localized values ("This field is required" /
  "Trường này là bắt buộc"). Updated `join-form.tsx` to use `useMemo`-wrapped
  Zod schemas that call `t('validation.required')` instead of hardcoded
  `'required'` strings, ensuring validation errors respect the user's locale.
- Fixed (WARNING 2 — LOW): Added Vitest test suite with 45 passing tests across
  4 files:
    - `use-join-meeting.reducer.test.ts` — 20 cases covering all reducer state
      transitions (lookup, request, SSE events, denial, retry).
    - `use-join-meeting.sse-backoff.test.ts` — 7 cases covering backoff delay
      values (1s/2s/4s) and the exhaustion behavior (no retry after 3rd
      attempt).
    - `use-join-meeting.handoff.test.ts` — 7 cases covering approval handoff
      from both REQUESTING and WAITING_APPROVAL phases and the RETRY reset.
    - `join-form.test.ts` — 11 cases covering schema validation and i18n message
      compatibility. Also exported `joinReducer` and `SSE_BACKOFF_DELAYS_MS`
      from `use-join-meeting.ts` to enable unit testing.

## [2026-04-25] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Replaced `response.data` casting pattern with `throwOnError: true`
  destructuring pattern to match existing SDK usage conventions in
  getMeetingByShortCode and requestJoin calls.
- Fixed: Replaced `state.phase === 'WAITING_APPROVAL' ? state.requestId : null`
  inline conditional in useEffect deps with a named `activeRequestId` variable
  to avoid invalid hook dependency expression.
- Fixed: Replaced non-existent `border-error-border` Tailwind class with
  `border-error/40` which correctly references the defined `--error` CSS
  variable.
- Fixed: Updated `next-intl/client` import for `useRouter` to `next/navigation`,
  matching the established pattern in all other components.
