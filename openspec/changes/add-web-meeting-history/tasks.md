## 1. i18n & navigation foundation

- [x] 1.1 Add `workspace.common.navHistory` key to
      `frontends/web/src/messages/en.json` and `vi.json`
- [x] 1.2 Extend `workspace.history.*` namespace in both `en.json` and `vi.json`
      with keys for: list section title, refresh label, refresh aria-label,
      load-more label, load-more loading label, load-more error toast
      title/description/retry, empty title/description/cta, error
      title/description/retry, untitled meeting fallback, type badge labels
      (scheduled, instant), status badge labels (scheduled, live, ended,
      cancelled), date/time format hints if needed, duration unit labels,
      cancelled aria suffix
- [x] 1.3 Extend `workspace.history.*` in both files with detail-screen keys:
      detail back label, type badge, status badge, description section title,
      participants section title with count, participants show-more label with
      count, recordings section title with count, recording row label with
      index, recording duration fallback, time range with duration, time start
      only, loading message, error title/description/retry, untitled fallback
      (reuse if same)
- [x] 1.4 Extend `workspace.history.*` in both files with player overlay keys:
      close label, error title/description, retry label, missing recording url
      message
- [x] 1.5 Update `frontends/web/src/components/workspace-shell.tsx` to add a
      third nav item
      `{ id: 'history', label: t('navHistory'), href: \`${basePath}/history\`
      }`between schedule and the right-side controls; ensure`activeTab='history'`
      highlights it

## 2. List feature — hook and types

- [x] 2.1 Create
      `frontends/web/src/components/meeting-history/use-meeting-history.ts`
      exporting a `useMeetingHistory()` hook with state union
      `{ phase: 'LOADING' } | { phase: 'EMPTY' } | { phase: 'ERROR', message: string } | { phase: 'SUCCESS', meetings: MeetingManagementMeetingResponse[], nextPageToken: string | null, isRefreshing: boolean, isLoadingMore: boolean }`
      and actions `{ retry, refresh, loadMore }`
- [x] 2.2 Implement initial-load flow: call `getMe()` once, then
      `listParticipatedMeetings({ path: { userId }, query: { pageSize: 20, status: 'ENDED,CANCELLED' }, throwOnError: true })`;
      on session-expired class of error from `ApiError`/`ApiFailError` set ERROR
      with `sessionExpired` message, otherwise generic error message
- [x] 2.3 Implement `refresh()`: keep prior `SUCCESS` items mounted, set
      `isRefreshing: true`, fetch page 1 (no `pageToken`); on success replace
      meetings + reset `nextPageToken`; on failure restore prior state and
      surface a sonner error toast (title from i18n)
- [x] 2.4 Implement `loadMore()`: no-op when not in `SUCCESS`, when
      `isLoadingMore` or `isRefreshing` is true, or when `nextPageToken` is
      null/empty; otherwise set `isLoadingMore: true`, call API with
      `pageToken`, append new meetings to existing array, update
      `nextPageToken`; on failure clear loading flag and surface sonner toast
      with Retry action that re-invokes `loadMore`
- [x] 2.5 Ensure pagination uses cursor only — never fall back to offset; ensure
      `getMe()` is called at most once per mount and reused across actions

## 3. List feature — UI components

- [x] 3.1 Create
      `frontends/web/src/components/meeting-history/meeting-history-card.tsx`
      rendering a single `MeetingManagementMeetingResponse`. Card shows status
      dot, title (or untitled fallback), date+start-time line, duration line,
      type badge; renders strikethrough title + 0.7 opacity + red CANCELLED
      badge when status is CANCELLED; aria-label includes localized cancelled
      suffix when applicable; entire card is keyboard-activatable (Enter/Space)
      and triggers `onClick(meeting)`
- [x] 3.2 Create
      `frontends/web/src/components/meeting-history/meeting-history-list.tsx`
      accepting state from `useMeetingHistory` and rendering states (loading
      skeleton with 5 placeholders, empty with icon + CTA back to workspace,
      error with retry, success with cards + Refresh button in section header +
      Load more button at footer). Refresh button is disabled with spin
      animation while `isRefreshing`; load-more button hidden when
      `nextPageToken` is null and shows loading affordance while
      `isLoadingMore`; clicking a card navigates to
      `/{locale}/workspace/history/{meeting.id}` via `useRouter().push`
- [x] 3.3 Update
      `frontends/web/src/components/meeting-history/meeting-history-screen.tsx`
      to render `<WorkspaceShell activeTab='history'>` with the new
      `MeetingHistoryList` (replaces the placeholder)
- [x] 3.4 Add `frontends/web/src/components/meeting-history/index.ts` barrel
      exporting public components and hooks ← (verify: list page renders all
      states correctly per spec scenarios in `web-meeting-history-list/spec.md`,
      including cancelled visual treatment, refresh-keeps-list, and load-more
      failure toast)

## 4. Detail feature — hook and components

- [x] 4.1 Create
      `frontends/web/src/components/meeting-history/use-meeting-detail.ts`
      exporting `useMeetingDetail(meetingId: string)` with state union
      `{ phase: 'LOADING' } | { phase: 'ERROR', message: string } | { phase: 'SUCCESS', detail: MeetingManagementMeetingDetailResponse }`
      and an action `retry`. Implementation calls `getMe()` then
      `getParticipatedMeetingDetail({ path: { userId, meetingId }, throwOnError: true })`;
      handles missing meetingId and auth errors with localized messages
- [x] 4.2 Create
      `frontends/web/src/components/meeting-history/participant-list.tsx`
      accepting `participants?: MeetingManagementMeetingParticipantResponse[]`.
      Hides itself for empty/null/undefined; renders section header with total
      count; shows first 5 with avatar (initials) + display name + role badge;
      when more exist, shows a "Show {N} more" button that on click expands the
      local state to render all
- [x] 4.3 Create
      `frontends/web/src/components/meeting-history/recording-player.tsx`
      overlay component. Props: `url: string | null`, `open: boolean`,
      `onClose: () => void`. Renders fixed-position overlay with
      `<video controls preload="metadata" src={url}>` when url present; tracks
      local `error` state set on `<video>` `onError` or when url is null/empty;
      in error state shows i18n message + Retry button that resets error and
      remounts/reloads the `<video>`; close button (and Esc key) calls
      `onClose`. Stops playback on close. Returns focus to the trigger element
      on close (use a `triggerRef` or similar)
- [x] 4.4 Create
      `frontends/web/src/components/meeting-history/meeting-detail-screen.tsx`
      accepting `meetingId: string`. Renders loading/error/success states using
      `useMeetingDetail`; success layout includes back button (popping to
      history list), title + untitled fallback, type badge, status badge, date
      (full locale style) + time-range with duration (or start-only when endTime
      missing), description (hidden when empty/whitespace), `ParticipantList`,
      recordings section (hidden when empty). Recording row click sets local
      `selectedRecordingUrl` and opens `RecordingPlayer` overlay; close clears
      the URL
- [x] 4.5 Create
      `frontends/web/src/app/[locale]/workspace/history/[meetingId]/page.tsx`
      rendering `<MeetingDetailScreen meetingId={params.meetingId} />` with the
      workspace shell ← (verify: detail page covers every scenario in
      `web-meeting-detail-view/spec.md` and `web-recording-playback/spec.md`,
      including section visibility rules and recording overlay error/retry)

## 5. Tests

- [x] 5.1 Create
      `frontends/web/src/components/meeting-history/use-meeting-history.test.ts`
      (Vitest + Testing Library renderHook). Cases: initial load success
      populates `SUCCESS` with first page; initial load empty enters `EMPTY`;
      initial load failure enters `ERROR`; getMe failure surfaces
      session-expired message; `refresh()` keeps list during refresh and
      replaces on success; `refresh()` failure restores prior state and surfaces
      toast (mock `sonner`); `loadMore()` appends and updates cursor;
      `loadMore()` no-ops when `nextPageToken` null; `loadMore()` failure keeps
      list and surfaces toast; double-invocation guarded
- [x] 5.2 Create
      `frontends/web/src/components/meeting-history/meeting-history-card.test.tsx`.
      Cases: ENDED meeting renders title without strikethrough + no cancelled
      badge; CANCELLED meeting renders strikethrough class on title, opacity-70
      on card, visible CANCELLED badge, aria-label with cancelled suffix;
      clicking card calls `onClick` with the meeting; pressing Enter and Space
      on a focused card also triggers `onClick`; untitled fallback used when
      title is missing ← (verify: tests pass on `pnpm --dir frontends/web test`
      and assert behavior described in spec scenarios)

## 6. Quality gates

- [x] 6.1 Run `pnpm --dir frontends/web lint:fix` and resolve any issues
      introduced by new files (Biome formatting)
- [x] 6.2 Run `pnpm --dir frontends/web build` and resolve any TypeScript errors
- [x] 6.3 Run `pnpm --dir frontends/web test` (or the project's vitest
      invocation) and confirm both new test files pass and no other tests
      regress ← (verify: every quality gate passes, no new lint warnings, build
      clean, tests green; confirm i18n keys exist in both en.json and vi.json by
      grep)
