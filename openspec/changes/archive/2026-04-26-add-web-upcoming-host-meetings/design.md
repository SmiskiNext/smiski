# Context

The web frontend already fetches host meetings on the workspace home screen, but
it currently renders all returned meetings with minimal card content and no
detail workflow. The requested change brings the web experience closer to the
existing Android upcoming-host-meetings behavior while fitting current web
patterns: generated SDK calls, next-intl localization, shadcn UI components,
locale-aware navigation, and discriminated-union loading state management.

The implementation spans multiple frontend concerns in one user flow: data
selection, reusable list/card/detail components, destructive action
confirmation, optimistic list updates after cancellation, and localized action
feedback. No backend changes are required, so the design should minimize
duplication and isolate meeting-specific logic away from `WorkspaceHomeScreen`.

## Goals / Non-Goals

**Goals:**

- Provide a reusable web upcoming-meetings module that owns fetching, filtering,
  sorting, and host actions for upcoming meetings.
- Ensure the workspace home screen only shows meetings that are both `SCHEDULED`
  and in the future, sorted by nearest `startTime` first.
- Add richer meeting cards and a detail dialog or sheet with consistent host
  actions and read-only settings summary.
- Preserve existing web error-handling and UI-state conventions, including
  localized success and failure feedback.
- Keep the feature aligned with the current generated SDK and existing
  `MeetingSettingsDialog` integration.

**Non-Goals:**

- Changing backend APIs, response contracts, or meeting domain rules.
- Adding edit-meeting flows, calendar integrations, or new meeting-room routes
  beyond using the existing green-room entry path.
- Replacing the existing meeting settings dialog implementation.
- Introducing server-side filtering or pagination changes for host meetings.

## Decisions

### 1. Encapsulate upcoming meetings in a dedicated feature directory

The implementation will introduce
`frontends/web/src/components/upcoming-meetings/` with a custom hook plus
focused presentational components. This keeps `WorkspaceHomeScreen` responsible
only for layout composition and allows the list, card, detail, and cancel flows
to share action handlers and formatting behavior.

Alternative considered: keep all state and JSX in `workspace-home-screen.tsx`.
This was rejected because the added detail dialog, cancellation flow, and action
feedback would make the screen component harder to maintain and test.

### 2. Perform filtering and sorting in the client hook after `listHostMeetings()`

`use-upcoming-meetings.ts` will load the host meetings once, normalize the
response array, filter to meetings where `status === 'SCHEDULED'` and parsed
`startTime` is after the current time, then sort ascending by `startTime`. The
hook will expose the existing discriminated-union state shape plus actions for
refresh, cancellation, and selection.

Alternative considered: add filtering inline inside the list component. This was
rejected because filtering and mutation behavior belong with the fetched data
source, and the same filtered collection must drive both list rendering and
post-action updates.

### 3. Reuse existing SDK meeting payloads instead of refetching detail on card click

The detail dialog will be driven primarily from the selected meeting already
returned by `listHostMeetings()`. This response type already contains the title,
description, time range, type, status, short code, and settings summary required
by the UI. The design avoids an extra `getMeeting()` call unless future
requirements demand fresher or richer detail.

Alternative considered: always fetch `getMeeting({ id })` when opening the
detail dialog. This was rejected because it adds latency, error cases, and
duplicated loading state without new information for the current scope.

### 4. Treat start/join actions as route handoff, with optional start API for scheduled meetings reserved for implementation review

The user-facing action button label and navigation target will follow the
requested behavior: `Start` for `SCHEDULED` and `Join` for `LIVE`, both
navigating to `/${locale}/workspace/green-room?code=${shortCode}`. Because the
existing route already owns the host handoff flow, the upcoming-meetings module
should not duplicate room-launch logic.

Alternative considered: call `startMeeting()` directly from the list before
routing scheduled meetings. This remains a possible implementation detail, but
it is not required to satisfy the requested frontend behavior and would
introduce extra loading and failure states. The initial design therefore keeps
the route handoff as the core requirement and leaves explicit pre-navigation
start semantics out of scope unless the green-room flow proves insufficient.

### 5. Use local mutation plus refresh semantics for cancellation

Cancelling a meeting will use a confirmation dialog, call
`cancelMeeting({ path: { id } })`, provide localized success or error feedback,
and remove the cancelled meeting from the rendered collection. The hook should
support immediate local removal after a successful response and optionally
trigger a refresh to stay aligned with server state.

Alternative considered: always force a full refetch after cancellation before
updating the UI. This was rejected because local removal gives faster feedback
and the dataset is already available in memory.

### 6. Keep action handling centralized and prevent card-click conflicts

The meeting card surface will open the detail dialog when the non-action area is
clicked, while nested buttons for start/join, copy link, settings, and cancel
will stop event propagation. Shared action handlers exposed by the hook will be
reused by both the card and detail dialog to keep labels, navigation, and
feedback consistent.

Alternative considered: separate implementations for card actions and detail
actions. This was rejected because it increases the chance of diverging behavior
and translation usage.

## Risks / Trade-offs

- [Time-based filtering depends on client clock accuracy] → Mitigation: use a
  single `new Date()` reference per load cycle and parse timestamps
  consistently; document that this mirrors the Android client-side behavior.
- [Scheduled meetings may require backend start semantics before green-room
  entry] → Mitigation: keep the spec focused on route handoff and verify the
  existing green-room flow with scheduled meetings during implementation; add
  explicit `startMeeting()` integration only if testing shows it is required.
- [Local cancellation removal can drift from server state if the API responds
  unexpectedly] → Mitigation: remove only after a successful API result and keep
  a refresh path available from the hook.
- [Using list payload for detail view means any fields omitted from
  `listHostMeetings()` cannot appear in the dialog] → Mitigation: scope the
  detail dialog to fields already present in the existing response shape and
  revisit `getMeeting()` only if a concrete missing field is identified.

## Migration Plan

1. Add the new upcoming-meetings components and hook behind the existing
   workspace home screen surface.
2. Replace the inline home-screen meeting list rendering with the new
   `UpcomingMeetingList` composition.
3. Add the required English and Vietnamese translation keys.
4. Verify loading, empty, success, card actions, detail dialog, and cancellation
   flows in the web workspace.
5. Rollback, if needed, by restoring the previous `WorkspaceHomeScreen`
   rendering and removing the new feature directory; no backend or data
   migration is involved.

## Open Questions

- None for spec creation. The only implementation checkpoint is validating
  whether the existing green-room flow already performs any required
  scheduled-meeting start transition, or whether `startMeeting()` must be
  invoked before navigation.
