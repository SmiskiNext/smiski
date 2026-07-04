# Context

The Android app already follows MVVM + Clean Architecture with Java, Hilt,
`CompletableFuture`, `LiveData`, and XML layouts. The dashboard and calendar
screens exist, but both currently render placeholder UI instead of real host
meeting data: `DashboardFragment` toggles between a static `meetingsContainer`
and an empty state, while `CalendarFragment` and `CalendarViewModel` are mostly
stubs and `CalendarRepository` / `CalendarEvent` are empty shells.

This change spans dependency management, domain/data mapping, repository
contracts, use cases, view models, fragments, adapters, and multiple layouts. It
also introduces a new external UI dependency (`com.kizitonwose.calendar:view`)
and must preserve existing codebase patterns rather than introducing Kotlin,
Flow, or custom state-management approaches.

## Goals / Non-Goals

**Goals:**

- Replace dashboard placeholder cards with real upcoming host meetings sourced
  from `MeetingsApi.listHostMeetings()`.
- Introduce a small dashboard-focused domain model (`UpcomingMeeting`) so the
  dashboard can remain decoupled from generated DTOs.
- Fill the existing calendar domain/repository/use-case/view-model path so
  `CalendarFragment` can render host meetings by month and date.
- Replace the manual calendar strip UI with a proper horizontally scrolling
  `CalendarView` while preserving the current top bar and events section layout.
- Keep implementation aligned with existing Android patterns: Java, Hilt
  injection, `MutableLiveData`, `UiState`, `CompletableFuture`, `ListAdapter`,
  and mapper-driven DTO conversion.

**Non-Goals:**

- Changing backend API contracts, pagination semantics, or meeting filtering on
  the server.
- Building full infinite-scroll pagination for dashboard or calendar host
  meetings in this change.
- Redesigning the events section below the calendar beyond wiring it to real
  selected-date data.
- Adding guest or participant calendar views; this scope is host-only.

## Decisions

### 1. Reuse `MeetingsApi.listHostMeetings()` for both dashboard and calendar

Both features will use the generated host meetings endpoint instead of adding
new API surfaces. The dashboard will request `pageSize=20` and filter in memory
for `SCHEDULED` meetings whose `startTime` is in the future, then sort ascending
by `startTime`. The calendar repository will request `pageSize=100` for the
visible month range and filter locally by date overlap.

**Rationale:** This matches the requested data source, avoids backend changes,
and keeps Android behavior inside existing repository abstractions.

**Alternatives considered:**

- Add a new dedicated upcoming meetings endpoint: rejected because it requires
  backend contract work.
- Expose generated DTOs directly to the UI: rejected because existing code uses
  domain models and mappers.

### 2. Keep dashboard and calendar models separate

The dashboard will use a new `UpcomingMeeting` record, while the calendar will
use the existing `CalendarEvent` type converted into a record.

**Rationale:** The dashboard only needs a concise meeting-card payload, while
calendar event rendering has different semantics and is already represented by a
separate domain type. Using separate records keeps each UI surface focused and
avoids leaking unnecessary fields.

**Alternatives considered:**

- Reuse `MeetingCreationResult` or another existing meeting model: rejected
  because those types represent different workflows and would blur intent.
- Create one generic `MeetingSummary` for both screens: rejected because it adds
  an abstraction the codebase does not currently need.

### 3. Model async UI state with existing `UiState<T>` and one-shot navigation/events with existing fragment behavior

`DashboardViewModel` will add
`MutableLiveData<UiState<List<UpcomingMeeting>>> upcomingMeetingsState`,
initialized and loaded from the constructor. `CalendarViewModel` will expose
`selectedDate`, `currentMonth`, `monthEvents`, and `selectedDateEvents` through
`MutableLiveData`, loading data with `CompletableFuture` and posting results on
the main executor.

**Rationale:** This is the app's established presentation pattern and keeps
fragment code reactive without introducing new reactive libraries.

**Alternatives considered:**

- Use Flow/StateFlow: rejected because it conflicts with project conventions.
- Use direct repository calls in fragments: rejected because it breaks MVVM
  boundaries.

### 4. Follow existing `ListAdapter` structure for upcoming meetings

`UpcomingMeetingAdapter` will mirror `MeetingHistoryAdapter` patterns: nested
`ViewHolder`, static `DiffUtil.ItemCallback`, layout inflation in
`onCreateViewHolder()`, formatting helpers inside the holder, and a listener
interface for the join action.

**Rationale:** This reduces implementation risk, makes the new adapter feel
native to the codebase, and gives the dashboard a stable rendering path.

**Alternatives considered:**

- Use a plain `RecyclerView.Adapter`: rejected because the app already benefits
  from `ListAdapter` diffing.

### 5. Use Kizitonwose binders instead of custom RecyclerView calendar logic

`fragment_calendar.xml` will embed `CalendarView` with dedicated day and
month-header layouts. `CalendarFragment` will configure the visible range
(`YearMonth.now() ± 100 months`), bind day cells through
`MonthDayBinder<DayViewContainer>`, and update the month title and data loads
through `monthScrollListener`.

**Rationale:** The library is purpose-built for this UI, reduces manual
date-strip complexity, and still works cleanly with XML/Java.

**Alternatives considered:**

- Extend the existing static strip with custom month math: rejected because it
  would remain brittle and hard to scale.
- Replace the entire screen with another calendar library: rejected because the
  requested dependency is explicit.

### 6. Keep the events section below the calendar and drive it from selected-date state

The lower events section will remain in place, but the placeholder content will
be replaced by selected-date event rendering driven by `selectedDateEvents`.
Empty state visibility will continue to be toggled in the fragment.

**Rationale:** This satisfies the requested feature while minimizing unnecessary
layout churn.

## Risks / Trade-offs

- **[Risk] Host meetings endpoint is cursor-paginated, but this change only
  reads the first page for dashboard/calendar loads** → **Mitigation:** document
  the page-size assumptions in the design/tasks and keep the repository methods
  isolated so multi-page loading can be added later without UI refactors.
- **[Risk] Filtering by `OffsetDateTime` and local date boundaries can produce
  off-by-one-day issues across time zones** → **Mitigation:** keep filtering in
  `OffsetDateTime` for repository range checks and derive `LocalDate` only when
  grouping events for day indicators.
- **[Risk] CalendarView binder state can render stale selected/highlight states
  when recycled** → **Mitigation:** require explicit rebinding of selected
  state, in/out month visibility, and event dot visibility on every bind, plus
  `notifyDateChanged()` when selection changes.
- **[Risk] Dashboard now has two asynchronous concerns (instant meeting creation
  and upcoming meeting loading)** → **Mitigation:** keep separate `LiveData`
  channels so loading and error handling do not overwrite one another.
- **[Trade-off] Reusing a single endpoint keeps implementation small but may
  overfetch meetings for the calendar month view** → **Mitigation:** cap initial
  page size at 100 and isolate filtering in `CalendarRepositoryImpl` so a
  dedicated API can be swapped in later.

## Migration Plan

1. Add the Kizitonwose dependency to the shared version catalog and Android app
   Gradle file.
2. Introduce the new/filled domain models, repository contracts, mapper methods,
   and use cases.
3. Update repository implementations and Hilt bindings so dashboard/calendar
   data paths compile.
4. Replace placeholder dashboard and calendar layouts with
   RecyclerView/CalendarView-driven layouts.
5. Update dashboard and calendar view models/fragments to load and observe live
   data.
6. Validate build compilation and smoke-test dashboard join and calendar date
   selection behavior.

Rollback is low risk: the change is isolated to the Android app and can be
reverted by restoring the previous layouts and removing the added
dependency/model wiring.

## Open Questions

- None for implementation. The change will proceed with the explicit assumption
  that first-page host meeting data is sufficient for the initial dashboard and
  month calendar experience.
