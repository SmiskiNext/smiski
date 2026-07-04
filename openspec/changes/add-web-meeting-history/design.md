## Context

The web client at `frontends/web/` is a Next.js 16 / React 19 app that uses the
generated SDK (`@/generated/sdk.gen.ts`) to talk to the `meeting-management`
service. Today, `/[locale]/workspace/history` renders a static "coming soon"
placeholder via `MeetingHistoryScreen`, while the Android app already consumes
the same backend endpoints (`listParticipatedMeetings`,
`getParticipatedMeetingDetail`) to provide a full history experience with list,
detail, participants, and recording playback.

Existing patterns we mirror:

- `frontends/web/src/components/upcoming-meetings/use-upcoming-meetings.ts` —
  state-machine hook with `phase: 'LOADING' | 'SUCCESS' | 'EMPTY' | 'ERROR'`,
  direct SDK calls, error handling via `ApiError` / `ApiFailError` from
  `@/lib/api/types.ts`.
- `frontends/web/src/components/upcoming-meetings/upcoming-meeting-card.tsx` —
  card layout, `next-intl` translations, locale-aware date formatting,
  shadcn-style UI primitives.
- `frontends/web/src/components/workspace-shell.tsx` — workspace layout shell
  with active-tab-aware nav.
- `frontends/web/src/components/account-settings/use-user-account-settings.ts`
  and `frontends/web/src/components/meeting/index.tsx` — `getMe()` for
  retrieving the current user id.
- Sonner toasts wired into the root layout (`Toaster` in
  `frontends/web/src/app/layout.tsx`).

Constraints:

- Biome (not ESLint/Prettier) is the linter/formatter; tests run on Vitest.
- The generated SDK already exposes `listParticipatedMeetings` and
  `getParticipatedMeetingDetail` with the right types; we do NOT edit
  `frontends/web/src/generated/`.
- i18n must support both `en` and `vi` (current locales), via
  `frontends/web/src/messages/`.
- All new code follows the repo's "no inline comments, self-documenting"
  convention (per `~/.claude/CLAUDE.md`); JSDoc only for exported components or
  hooks with non-trivial behavior.

Stakeholders: end users who want a web parity experience with Android; the
existing Android `meeting-history-list`, `meeting-detail-view`, and
`recording-playback` capabilities (independent specs we draw inspiration from).

## Goals / Non-Goals

**Goals:**

- Replace the placeholder history screen with a real list driven by
  `listParticipatedMeetings(userId, pageSize, pageToken, status)` filtered to
  `ENDED,CANCELLED`.
- Provide a dynamic detail route at `/[locale]/workspace/history/[meetingId]`
  driven by `getParticipatedMeetingDetail(userId, meetingId)`.
- Match Android's user-facing semantics (cancelled treatment, participants
  preview, recordings list, badge layout) while using web-native interaction
  (button-driven Refresh and Load more, HTML5 video).
- Add a `History` entry to the workspace primary nav so the screen is
  discoverable.
- Translate all new copy in English and Vietnamese.
- Cover the list hook (state machine + pagination merge) and the card cancelled
  visual with automated tests.

**Non-Goals:**

- Backend changes, OpenAPI changes, or SDK regeneration.
- Search, sorting, or any client-side filter beyond the fixed `ENDED,CANCELLED`
  status filter.
- Editing or deleting recordings, participants, or any history item.
- Pre-fetching or background refresh of recordings.
- Recording download or share flows.
- Offline / cache layer for history data.
- Pull-to-refresh (Android-only interaction; not used on web desktop).
- Infinite scroll auto-pagination (deliberately replaced by an explicit Load
  more button).

## Decisions

### Detail screen lives on its own route

`/[locale]/workspace/history/[meetingId]` is a full Next.js page rather than a
modal or sheet. The detail content (long participant list, recordings with an
overlay video player) is too heavy for an overlay, and a route lets the URL be
shared and the back button work naturally.

Alternative considered: a side sheet like `MeetingDetailSheet` used for upcoming
meetings. Rejected because the overlay video player overlays the sheet awkwardly
and the participants/recordings sections often exceed the sheet's vertical
space.

### Inline HTML5 `<video>` for playback

Recordings open in an in-page overlay containing a native `<video controls>`
element with `preload="metadata"`. Errors raised by the element surface a
"Recording failed to load" state with a Retry button.

Alternative considered: a third-party player such as Plyr, Video.js, or HLS.js.
Rejected because the existing Android client uses ExoPlayer's native paths and
the web backend serves direct MP4 URLs (no HLS pipeline today). Native `<video>`
is sufficient, smaller, and accessible by default.

### Explicit "Load more" instead of infinite scroll

A button at the end of the list calls `loadMore()` until `nextPageToken` is
`null`. This avoids the Android pattern (auto-fetch when scroll nears bottom)
because:

- Web users frequently scroll up to compare items, and infinite scroll resets
  intent.
- A button is keyboard-accessible without extra ARIA work.
- Matches the user's stated preference.

### Refresh button instead of pull-to-refresh

A `Refresh` button (with `RefreshCw` icon from lucide-react) sits in the section
header. While refreshing, the existing list stays mounted with reduced opacity
so layout doesn't jump.

### State machine for the list hook

`UpcomingMeetingsState` already defines a `phase`-tagged union. We extend the
same shape for history but track pagination metadata when in `SUCCESS`:

```
type MeetingHistoryState =
  | { phase: 'LOADING' }
  | { phase: 'EMPTY' }
  | { phase: 'ERROR' }
  | {
      phase: 'SUCCESS';
      meetings: MeetingManagementMeetingResponse[];
      nextPageToken: string | null;
      isRefreshing: boolean;
      isLoadingMore: boolean;
    };
```

This keeps the rendering switch trivially exhaustive in TypeScript and parallels
the Android `MeetingHistoryUiState`.

Pagination merge: `loadMore` calls the API with the saved cursor, then appends
the new page to the existing `meetings` array (cursor pagination, not replace).

Refresh: re-fetches page 1 with `pageToken: undefined`. On success, replaces the
list. On failure, keeps the current list and surfaces a toast (consistent with
Android behavior).

LoadMore failure: keeps existing items, sets `isLoadingMore: false`, and
surfaces a sonner toast with a Retry action.

### `getMe()` once per hook to derive `userId`

Both the list and the detail hook call `getMe()` first, store the resulting id,
and pass it into `listParticipatedMeetings` / `getParticipatedMeetingDetail`. If
`getMe()` fails (401 / network), the hook enters `ERROR` with a "Please sign in
again" message — same wording the Android app uses.

### Cancelled visual treatment

Translated 1:1 from Android (`MeetingHistoryAdapter`):

- Title gets `line-through` decoration.
- Card receives `opacity-70`.
- A `CANCELLED` badge with `bg-error-subtle text-error` (Tailwind tokens already
  present in the design system) appears next to the title.
- The `aria-label` says `"<title> — cancelled"` so screen readers announce the
  state.

### i18n namespace layout

All copy lives under `workspace.history.*` (matching the existing partial
namespace) plus one new `workspace.common.navHistory` key for the nav label.
Date and time formatting uses the user's locale via `Date.prototype` methods,
not hard-coded English formats — same approach as `upcoming-meeting-card.tsx`.

### Test surface

- `use-meeting-history.test.ts` (Vitest + React Testing Library): mocks `getMe`,
  `listParticipatedMeetings`. Covers initial load (success / empty / error),
  refresh (success / failure-keeps-list), loadMore (append + cursor exhaustion +
  failure), and session-expired path.
- `meeting-history-card.test.tsx`: renders an ENDED card and a CANCELLED card
  asserting the strikethrough class, opacity, badge presence, and aria-label.

### Detail data shape and section visibility

`MeetingManagementMeetingDetailResponse` includes optional `participants` and
`recordings` arrays; both can be `undefined`. The detail screen treats
`undefined` and `[]` identically — section is hidden. Description is hidden if
the string is `null`, `undefined`, or whitespace-only.

Participants preview shows the first 5 entries; if there are more, an expand
button labeled "Show {N} more" reveals the rest. State for whether the list is
expanded is local to the detail screen.

## Risks / Trade-offs

- **Recording URLs may be presigned with a short TTL** → If the page sits idle
  long enough, a previously fetched URL could 401/403. Mitigation: the player
  surfaces an error state with a Retry button that re-issues the recording click
  handler (which re-fetches the detail if the URL is null).
- **`<video>` accessibility on mobile Safari** → Native player is generally
  fine, but full-screen behavior is browser-dependent. Mitigation: rely on
  browser-native controls and full-screen API; do not implement custom controls.
  → User-facing fallback: keyboard close (Esc) and explicit Close button on the
  overlay.
- **Cursor pagination drift after refresh** → If new meetings ENDED while the
  user has paged forward, refresh resets to page 1 and discards the deeper
  pages. Mitigation: matches Android behavior; the user can re-paginate. This is
  acceptable for a history view.
- **Sonner toast availability** → `Toaster` is mounted in the root layout
  (`frontends/web/src/app/layout.tsx`). No mitigation needed beyond importing
  `toast` from `sonner`.
- **i18n drift** → Two translation files must stay in sync. Mitigation: tasks
  list explicitly enumerates the keys to add; reviewers confirm both files were
  edited.

## Migration Plan

No migration. Replacing a placeholder with a real screen and adding a new route.
Rollback is a revert of the change.

## Open Questions

(none — autopilot answered all open questions during exploration)
