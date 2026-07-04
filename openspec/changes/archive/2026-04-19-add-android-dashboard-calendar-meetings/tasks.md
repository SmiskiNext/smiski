# Tasks

## 1. Wire dependencies and domain contracts

- [x] 1.1 Add the Kizitonwose calendar version and library aliases to
      `gradle/libs.versions.toml`
- [x] 1.2 Add `implementation(libs.kizitonwose.calendar.view)` to
      `frontends/android-app/app/build.gradle.kts`
- [x] 1.3 Create `UpcomingMeeting` as a Java record in `domain/model/` and
      convert `CalendarEvent` into the requested Java record
- [x] 1.4 Extend `MeetingRepository` and `CalendarRepository`, fill
      `GetCalendarEventsUseCase`, and add `GetUpcomingMeetingsUseCase` to match
      the new host-meeting read flows ← (verify: domain APIs compile cleanly and
      signatures match proposal/design exactly)

## 2. Implement repository and mapper support for host meeting reads

- [x] 2.1 Add
      `MeetingMapper.toUpcomingMeeting(MeetingManagementMeetingResponse)` and
      any needed calendar mapping helpers
- [x] 2.2 Implement `MeetingRepositoryImpl.getUpcomingHostMeetings()` using
      `MeetingsApi.listHostMeetings(20, null)`, future-only `SCHEDULED`
      filtering, mapping, and ascending sort
- [x] 2.3 Implement `CalendarRepositoryImpl.getEventsForDateRange()` using
      `MeetingsApi.listHostMeetings(100, null)`, date-range filtering, and
      `CalendarEvent` mapping
- [x] 2.4 Confirm `RepositoryModule` binds the completed
      `CalendarRepositoryImpl` contract without duplicate or missing Hilt
      bindings ← (verify: repository layer follows existing patterns, executor
      usage stays on IO, and both dashboard/calendar read paths are injectable)

## 3. Deliver dynamic upcoming meetings on the dashboard

- [x] 3.1 Replace the hardcoded `meetingsContainer` cards in
      `fragment_dashboard.xml` with `RecyclerView#rvUpcomingMeetings` while
      preserving the existing empty state include
- [x] 3.2 Create `item_upcoming_meeting.xml` matching the current dashboard card
      styling with icon, title, time range, and Join/Wait action button
- [x] 3.3 Implement `UpcomingMeetingAdapter` as a
      `ListAdapter<UpcomingMeeting, ViewHolder>` following
      `MeetingHistoryAdapter` conventions and join-click callback behavior
- [x] 3.4 Update `DashboardViewModel` to inject `GetUpcomingMeetingsUseCase`,
      expose
      `MutableLiveData<UiState<List<UpcomingMeeting>>> upcomingMeetingsState`,
      and load data from initialization
- [x] 3.5 Update `DashboardFragment` to configure the RecyclerView, observe
      `upcomingMeetingsState`, toggle empty/list/error rendering, and launch
      `VideoCallActivity` from item join actions ← (verify: dashboard shows
      loading/success/empty/error states correctly and join launches use the
      selected meeting short code)

## 4. Replace the calendar strip with CalendarView-driven host events

- [x] 4.1 Replace the manual month navigation and hardcoded weekday strip in
      `fragment_calendar.xml` with a month title `TextView` and
      `com.kizitonwose.calendar.view.CalendarView`, keeping the top bar and
      lower events section
- [x] 4.2 Create `calendar_day_layout.xml` and
      `calendar_month_header_layout.xml`, plus any required string resources
      such as weekday abbreviations and calendar labels
- [x] 4.3 Add `DayViewContainer` and implement the `CalendarFragment` binders
      (`MonthDayBinder` and header binder), calendar setup range,
      first-day-of-week handling, and month scroll listener
- [x] 4.4 Update `CalendarViewModel` to inject the calendar use case or
      repository, expose `selectedDate`, `currentMonth`, `monthEvents`, and
      `selectedDateEvents`, and load data for month/date selection changes
- [x] 4.5 Connect `CalendarFragment` observers so day selection updates the
      calendar highlight, month changes trigger event loading, and the lower
      events section reflects selected-date data and empty state ← (verify:
      visible month title, selected date, event dots, and selected-date event
      list stay synchronized during scroll and tap interactions)

## 5. Validate integration and regressions

- [x] 5.1 Run the Android app build or targeted compile validation for the
      updated module
- [x] 5.2 Smoke-test dashboard host upcoming meetings and calendar host events
      against the generated API models, documenting any first-page pagination
      limitations if observed ← (verify: feature compiles end-to-end, no
      placeholder UI remains on the targeted surfaces, and known pagination
      assumptions are explicitly noted)
