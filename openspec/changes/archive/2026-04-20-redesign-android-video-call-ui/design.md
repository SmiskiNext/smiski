# Context

The Android app already has a working LiveKit-backed video-call shell, host
upcoming-meetings list, and schedule-meeting form, but the meeting experience is
split across older UI assumptions. `ActiveCallFragment` still uses a six-button
primary control bar, `CallViewModel` does not own layout-selection or
host-settings state, and dashboard upcoming meeting cards only expose a join
action. At the same time, the backend already supports
`PUT /api/v1/meetings/{id}/settings` for both `SCHEDULED` and `LIVE` meetings,
which makes it possible to expose meeting-settings edits without adding a new
server contract.

This change spans multiple Android presentation surfaces and touches
presentation, domain, data, and resource layers inside the existing MVVM + Clean
Architecture structure. The redesign must preserve call reliability, keep the
primary call controls reachable within 48dp touch targets, and fit naturally
into the current `BottomSheetDialogFragment`-based Android patterns already used
for chat and participants.

## Goals / Non-Goals

**Goals:**

- Reduce primary in-call chrome by moving secondary actions into an overflow
  bottom sheet while keeping microphone, camera, end-call, and a single
  more-actions entry point immediately accessible.
- Introduce user-selectable video layout modes backed by activity-scoped
  `CallViewModel` state so the chosen arrangement survives fragment rebinds and
  call-surface redraws.
- Expose host-only in-meeting settings editing during LIVE meetings using the
  existing meeting-settings replacement API.
- Add per-upcoming-meeting dashboard actions, including edit entry into a
  pre-meeting settings flow.
- Reuse existing Android architecture and components rather than introducing
  Compose or a parallel presentation stack.

**Non-Goals:**

- Rebuilding the video-call UI in Jetpack Compose.
- Delivering a fully custom Spotlight or Sidebar renderer beyond a stable
  phase-1 implementation/fallback.
- Adding a new backend endpoint for updating scheduled meeting title, date, or
  duration metadata.
- Changing backend meeting-settings semantics or the
  `PUT /api/v1/meetings/{id}/settings` contract.

## Decisions

### 1. Use bottom sheets for secondary call actions and settings

The redesign will introduce three focused bottom sheets: meeting actions, layout
picker, and host meeting settings. This keeps the main call surface visually
lighter, aligns with the existing participants/chat interaction model, and
avoids overloading the control bar with low-frequency actions.

**Why this approach**

- `BottomSheetDialogFragment` already exists in the Android call flow and fits
  the current XML/Fragment stack.
- Bottom sheets let the call surface remain visible while presenting
  context-specific actions.
- Overflow and settings content can evolve independently of the main layout XML.

**Alternatives considered**

- Adding more icon buttons back into the control bar would preserve direct
  access but would recreate the current crowding problem.
- Using a top-right popup menu would be lighter-weight, but it is less suitable
  for rich rows with badges, counts, and host-only visibility.

### 2. Keep layout selection as local UI state in `CallViewModel`

The selected layout will be represented by a new domain enum, `VideoLayout`, and
exposed from `CallViewModel` as observable state. `ActiveCallFragment` will
observe that state and apply the appropriate `RecyclerView`/layout-manager
configuration.

**Why this approach**

- Layout choice is a per-device presentation preference, not a server-owned
  meeting setting.
- `CallViewModel` is already activity-scoped and is the correct place to survive
  fragment lifecycle events and sheet dismissal/recreation.
- This avoids unnecessary backend coupling for a UI-only preference.

**Alternatives considered**

- Persisting layout on the server would add API scope and synchronize a
  preference that is likely client-specific.
- Storing layout only inside `ActiveCallFragment` would make the selection more
  fragile across recreation.

### 3. Phase layout behavior instead of blocking on fully custom renderers

`AUTO` will preserve the current dynamic span logic. `TILED` will force a
deterministic two-column grid. `SPOTLIGHT` and `SIDEBAR` will ship with stable
phase-1 arrangements that may internally fall back to the nearest supported
`RecyclerView` layout structure while still exposing distinct user choices and
selected-state feedback.

**Why this approach**

- The requested redesign needs the user-facing layout switcher now, even if
  richer renderer behavior arrives later.
- The existing grid-based call surface can support incremental evolution without
  a full rewrite.

**Alternatives considered**

- Delaying the layout picker until all four modes have bespoke renderers would
  slow delivery and block the improved UI.
- Implementing all layouts with custom nested recyclers in one iteration would
  increase risk across a large call-surface change.

### 4. Reuse the meeting-settings replacement API for both live and pre-meeting settings edits

Host settings updates during a call and pre-meeting settings edits from upcoming
meeting cards will both reuse `PUT /api/v1/meetings/{id}/settings`. The Android
app will load current meeting details for context, but this change will treat
settings replacement as the only committed edit path because there is no
existing backend endpoint for updating scheduled meeting title/time metadata.

**Why this approach**

- The backend contract already supports `SCHEDULED` and `LIVE` settings updates.
- Reusing the same repository pathway reduces duplicate networking logic and
  validation drift.
- It keeps the scope aligned with the user's stated pre-meeting settings goal
  instead of inventing a new server API.

**Alternatives considered**

- Adding a new backend update-meeting endpoint would broaden scope beyond this
  UI redesign.
- Duplicating a separate “edit settings” fragment would increase maintenance
  cost when `ScheduleFragment` already owns the relevant settings controls.

### 5. Extend upcoming meeting cards through adapter callbacks and menu resources

`UpcomingMeetingAdapter` will add a more-options callback, and
`DashboardFragment` will own popup-menu handling so navigation, clipboard
actions, calendar intents, and cancellation logic remain coordinated at the
fragment level.

**Why this approach**

- The adapter should stay responsible for item binding, not business/navigation
  decisions.
- `DashboardFragment` already owns the list screen context and can refresh state
  after mutations.
- A menu resource keeps the options localized and consistent with Android
  patterns.

**Alternatives considered**

- Embedding popup logic inside the adapter would couple view binding to
  navigation and side effects.
- Replacing the card menu with swipe actions would be harder to discover and
  less consistent with the rest of the app.

## Risks / Trade-offs

- **[Simplified Spotlight/Sidebar behavior may feel too similar in phase 1]** →
  Mitigate by documenting phase-1 fallback behavior in specs and keeping layout
  selection/state handling extensible.
- **[Multiple bottom sheets can create interaction overlap with auto-hide
  controls]** → Mitigate by centralizing sheet launch callbacks in
  `ActiveCallFragment` and resetting control visibility timers whenever a sheet
  is shown or dismissed.
- **[Live settings updates may drift from server truth after API
  normalization]** → Mitigate by reloading/refreshing `meetingSettings` in
  `CallViewModel` from the successful response instead of assuming optimistic
  local state is exact.
- **[Using `ScheduleFragment` for edit mode may imply broader rescheduling
  support than the backend currently offers]** → Mitigate by framing the first
  iteration as a pre-meeting settings edit flow, changing the CTA label to
  “Update,” and keeping unsupported fields read-only or clearly non-committing
  until a backend metadata-update endpoint exists.
- **[Dashboard options add more actions than the current data layer exposes]** →
  Mitigate by extending repository/view-model contracts in the same change
  rather than leaving menu items as dead ends.

## Migration Plan

1. Add the new Android resources, bottom-sheet layouts, icons, and menu
   definitions.
2. Extend domain/data contracts (`VideoLayout`, meeting-settings update methods,
   and any needed upcoming-meeting option actions).
3. Update `CallViewModel` and `ActiveCallFragment` to use the compact controls,
   overflow menu, layout selection, and host settings flow.
4. Update dashboard upcoming meeting cards and wire the options menu into
   edit/copy/calendar/cancel actions.
5. Add schedule-screen edit mode for pre-meeting settings updates.
6. Validate on-device/emulator with host and non-host meeting roles, including
   live-call settings changes and scheduled-meeting edit entry.

Rollback is low risk because the change is Android-client-only and additive at
the resource/class level. Reverting the app change restores the prior UI without
requiring backend rollback.

## Open Questions

- Should edit mode allow title/date/time changes in the UI as read-only context,
  or should those fields be fully editable but only partially saved once a
  future backend endpoint exists?
- What exact visual treatment should `SPOTLIGHT` and `SIDEBAR` use after phase 1
  once richer participant prioritization rules are available?
- Should copied meeting links use a canonical deep link/URL format, or should
  the first release copy the meeting short code/invite string until a share-link
  convention is standardized?
