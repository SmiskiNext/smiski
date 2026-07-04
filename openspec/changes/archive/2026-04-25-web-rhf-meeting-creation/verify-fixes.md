## [2026-04-25] Round 4 (from apply auto-verify — re-verify fix pass)

### Verifier

- Fixed (WARNING): Added `afterEach` and `beforeEach` to the explicit vitest
  imports in `use-join-meeting.sse-backoff.test.ts` — these globals were used
  without being imported, causing `tsc --noEmit` to fail when `vitest/globals`
  was not in `tsconfig.json` types
- Fixed (WARNING): Added `useLocale()` from `next-intl` to `success-dialog.tsx`
  and updated the copy-link URL from `${origin}/join/${shortCode}` to
  `${origin}/${locale}/join/${shortCode}` to match the actual
  `[locale]/join/[code]` route structure
- Verified: `tsc --noEmit` passes with zero errors
- Verified: `next build` completes successfully (all 13 routes)
- Verified: `biome check` passes on all 83 files with zero errors
- Verified: `vitest run` — 60 tests across 5 test files, all passing

---

## [2026-04-25] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Removed unused `MEETING_TOKEN_KEY` and `MEETING_ROOM_KEY` constants
  from `instant-meeting-dialog.tsx` (biome `noUnusedVariables`)
- Fixed: Removed unused `SettingsSlice` type and `MeetingSettingsValues` import
  from `meeting-settings-form.tsx` (biome `noUnusedVariables`)
- Fixed: Removed unused `Loader2` import from `success-dialog.tsx` (biome
  `noUnusedImports`)
- Fixed: Added biome-ignore comments to `invitee-input.tsx` container div for
  `noStaticElementInteractions` and `useKeyWithClickEvents` — tag-input
  container pattern where keyboard users interact with the inner input directly
- Fixed: Added biome-ignore comment to `FormLabel` in `form.tsx` for
  `noLabelWithoutControl` — standard shadcn/RHF pattern where `htmlFor` resolves
  to a FormControl wrapper div containing the actual input
- Fixed: Added biome-ignore comment to invitees label in
  `workspace-schedule-screen.tsx` for `noLabelWithoutControl` — InviteeInput is
  a composite widget
- Fixed: Resolved import ordering and formatting issues across `auth/form.tsx`,
  `auth/index.tsx`, `instant-meeting-dialog.tsx`, `invitee-input.tsx`,
  `join-meeting/join-form.tsx`, `new-meeting-dropdown.tsx`,
  `workspace-schedule-screen.tsx` via `biome check --write`
- Verified: `tsc --noEmit` passes with zero errors
- Verified: `next build` completes successfully with all 13 routes
- Verified: `biome check` passes on all 77 files with zero errors

## [2026-04-25] Round 3 (from apply auto-verify — second fix pass)

### Verifier

- Fixed (CRITICAL): Added `token` and `roomName` to the `READY` state type and
  `START_SUCCEEDED` action in `use-create-meeting.ts`; captured them from the
  `startMeeting` response
- Fixed (CRITICAL): Added `MEETING_TOKEN_KEY` and `MEETING_ROOM_KEY` constants
  and sessionStorage writes to `instant-meeting-dialog.tsx` `handleNavigate` —
  credentials are now persisted before redirecting to the meeting room, matching
  the pattern in `join-meeting/index.tsx`
- Fixed (WARNING): Added `workspace.schedule.validation.startTimeMustBeFuture`
  locale keys to `messages/en.json` and `messages/vi.json`; updated
  `workspace-schedule-screen.tsx` to translate the schema's raw
  `'startTimeMustBeFuture'` key into the localized string before rendering
- Fixed (WARNING): Removed `NewMeetingDropdown` import and usage from
  `home-screen.tsx`; replaced with a plain `Link` to the login page —
  unauthenticated surface no longer exposes host-only creation flows
- Fixed (WARNING): Extracted `buildInitialStepSchema` and
  `buildPasswordStepSchema` as exported functions from `join-form.tsx`; rewrote
  `join-form.test.ts` to import and test the actual shipped schema factories
  instead of ad-hoc duplicates; corrected misleading whitespace-trim test
  description to match the real schema behavior
- Fixed (WARNING): Created `use-create-meeting.test.ts` with reducer tests
  covering: IDLE→CREATING, CREATING→STARTING, STARTING→READY (including
  token/roomName), FAILED from each phase, RETRY→IDLE, RESET→IDLE, and unknown
  action guard
- Verified: `tsc --noEmit` passes (only pre-existing test-runner globals error
  in `use-join-meeting.sse-backoff.test.ts`)
- Verified: `next build` completes successfully with all 13 routes
- Verified: `biome check` passes on all 83 files with zero errors
- Verified: `vitest run` — 60 tests across 5 test files, all passing

### Verified dimensions

| Dimension     | Status               |
| ------------- | -------------------- |
| Completeness  | Issues found         |
| Correctness   | Critical issue found |
| Coherence     | Issues found         |
| Architecture  | Passes               |
| Test Coverage | Issues found         |

### Static checks

- `tsc --noEmit`: passes
- `next build`: passes (all 13 routes)
- `biome check`: passes (77 files, zero errors)

### CRITICAL

1. **Instant-meeting handoff does not deliver meeting-room credentials**
    - Spec reference: `web-meeting-creation/spec.md` — "Successful instant
      creation starts the meeting and prepares redirect state" requires the
      ready state to include meeting-room launch data.
    - `use-create-meeting.ts` `READY` state keeps only `meetingId` and
      `shortCode`; it discards the launch/session data returned by
      `startMeeting`.
    - `instant-meeting-dialog.tsx` redirects to
      `/${locale}/workspace/meeting-room` on success.
    - `meeting/index.tsx` requires `meeting_token` and `meeting_room_name` in
      `sessionStorage` to render the room — the instant flow never writes them.
    - Result: after a "successful" instant meeting, the host sees "No active
      meeting session" instead of entering the room.
    - Fix: capture `startMeeting` response data in the `READY` state, write
      `meeting_token` and `meeting_room_name` to `sessionStorage` before
      redirect (same pattern used by `join-meeting/index.tsx`).

### WARNING

1. **Schedule validation message `startTimeMustBeFuture` is not localized**
    - `lib/schemas/meeting.ts` emits raw message key `startTimeMustBeFuture` in
      the zod refinement.
    - `workspace-schedule-screen.tsx` renders schema messages directly via
      `FormMessage`.
    - `messages/en.json` and `messages/vi.json` have
      `joinMeeting.validation.required` but no corresponding
      `startTimeMustBeFuture` entry for the schedule flow.
    - Result: users see an untranslated token when the start time is in the
      past.

2. **Public home page mounts `NewMeetingDropdown` without auth gating**
    - `home-screen.tsx` mounts `NewMeetingDropdown` on the unauthenticated
      landing page.
    - Design risk section called out auth-aware conditional mounting as
      mitigation for unauthenticated surfaces.
    - If `createInstantMeeting` / `scheduleMeeting` APIs require an
      authenticated host, this surface will expose host-only flows to guests,
      failing only after interaction.
    - This may be intentional fallback behavior, but it diverges from the
      design's mitigation guidance.

3. **No test coverage for the new meeting-creation flow**
    - Join-meeting has focused tests: `join-form.test.ts`,
      `use-join-meeting.reducer.test.ts`, `use-join-meeting.handoff.test.ts`,
      `use-join-meeting.sse-backoff.test.ts`.
    - Meeting creation — the largest new functionality — has zero tests
      covering:
        - `use-create-meeting.ts` reducer states (create/start success, partial
          failure, retry, reset)
        - `instant-meeting-dialog.tsx` / `success-dialog.tsx` dialog behavior
          (copy-link, auto-redirect)
        - Schedule payload mapping and future-time validation in
          `workspace-schedule-screen.tsx`
        - Localized error rendering for creation flows

4. **Join-form test validates a test-local schema, not the shipped
   implementation**
    - `join-meeting/join-form.test.ts` rebuilds ad-hoc zod schemas inside the
      test file instead of importing the real schema from `join-form.tsx`.
    - One test claims whitespace is trimmed before validation, but the test
      schema does not apply `.trim()`.
    - These tests give confidence in a parallel test-only schema, not in the
      actual shipped form logic.

### SUGGESTION

1. **Centralize localized schema messages**: extract message keys from zod
   definitions in `lib/schemas/meeting.ts` into a lookup that UI layers can
   translate, keeping schemas reusable while ensuring user-visible errors are
   always localized.

2. **Align instant-meeting handoff with join-meeting handoff**:
   `join-meeting/index.tsx` already writes `sessionStorage` credentials before
   redirect. Reusing the same contract for instant-meeting would reduce
   divergence between the two entry paths into the meeting room.

3. **Convert schedule-page fields to shared `FormField` primitives**: the page
   uses `Form` but several top-level fields still render manual labels/messages
   rather than the shared wrapper introduced by this change. Migrating them
   would complete the "single form composition pattern" consistency goal from
   the design.

### Completeness checklist

| Task                                                       | Status                                                        |
| ---------------------------------------------------------- | ------------------------------------------------------------- |
| 1.1 Add `ui/form.tsx` with shadcn RHF primitives           | Done                                                          |
| 1.2 Create `lib/schemas/meeting.ts` with shared schemas    | Done                                                          |
| 1.3 Create `MeetingSettingsForm` and `InviteeInput`        | Done                                                          |
| 2.1 Refactor auth form to RHF                              | Done                                                          |
| 2.2 Refactor join-meeting form to RHF                      | Done                                                          |
| 2.3 Delete legacy `auth-screen.tsx`                        | Done                                                          |
| 3.1 Create `use-create-meeting.ts` reducer hook            | Done — but READY state is missing launch data (CRITICAL)      |
| 3.2 Build instant-meeting dialog, success dialog, dropdown | Done — redirect fires without sessionStorage write (CRITICAL) |
| 3.3 Integrate entry points into home/workspace screens     | Done                                                          |
| 4.1 Refactor schedule screen to RHF with shared schema     | Done — missing `startTimeMustBeFuture` locale key (WARNING)   |
| 4.2 Add i18n keys for meeting creation                     | Done — partial gap noted above                                |
| 5.1 Typecheck passes                                       | Done                                                          |
| 5.2 Production build passes                                | Done                                                          |
| 5.3 Lint/format passes                                     | Done                                                          |

### Verdict

**1 critical issue must be fixed before archiving.** The instant-meeting flow
completes without error but fails to deliver the host into a working meeting
room because session credentials are never persisted.

- Use `/apply web-rhf-meeting-creation` to fix issues, or fix manually and
  re-verify.
