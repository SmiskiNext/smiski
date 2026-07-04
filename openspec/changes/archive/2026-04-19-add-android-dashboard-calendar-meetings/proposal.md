# Why

The Android app still shows hardcoded placeholder meetings on the dashboard and
a static mock calendar, which prevents hosts from seeing their real upcoming
schedule or using the calendar screen as a functional planning tool. This change
is needed now to connect both surfaces to the existing host meetings API and
bring the Android experience in line with the app's meeting-management
capabilities.

## What Changes

- Replace the dashboard's hardcoded upcoming meeting cards with a dynamic
  host-only upcoming meetings list backed by `MeetingsApi.listHostMeetings()`.
- Add a new `UpcomingMeeting` domain model, repository method, mapper
  conversion, use case, ViewModel state, and `ListAdapter` so the dashboard can
  load, sort, and display real scheduled future meetings.
- Update `DashboardFragment` and `fragment_dashboard.xml` to render a
  `RecyclerView`, preserve the existing empty state, and allow hosts to launch
  `VideoCallActivity` from each meeting item.
- Replace the static calendar strip and manual month navigation in
  `fragment_calendar.xml` with `com.kizitonwose.calendar:view:2.10.1`.
- Fill the currently empty calendar domain, repository, use case, and ViewModel
  layers so the calendar can load host meetings for a visible month, expose
  selected-date event lists, and show event indicators on days with meetings.
- Add the new calendar day/header layouts, required strings, and Gradle
  version-catalog dependency wiring for the calendar library.

## Capabilities

### New Capabilities

- `android-host-upcoming-meetings`: Display real upcoming meetings for the
  current host on the Android dashboard, including loading, empty, success, and
  join behaviors.
- `android-host-calendar-view`: Display a host meeting calendar in the Android
  app using Kizitonwose CalendarView with month navigation, selected-date
  events, and day indicators.

### Modified Capabilities

- None.

## Impact

- Affected Android presentation code: dashboard and calendar fragments, view
  models, adapters, and XML layouts in `frontends/android-app/app`.
- Affected Android domain/data layers: `MeetingRepository`,
  `CalendarRepository`, `MeetingRepositoryImpl`, `CalendarRepositoryImpl`,
  `MeetingMapper`, new use cases, and new domain models.
- Affected dependency management: `gradle/libs.versions.toml` and
  `frontends/android-app/app/build.gradle.kts`.
- Uses existing backend API `GET /api/v1/meetings` via generated
  `MeetingsApi.listHostMeetings(pageSize, pageToken)` without backend contract
  changes.
