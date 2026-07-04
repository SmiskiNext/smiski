# ADDED Requirements

## Requirement: Calendar screen uses an interactive month calendar

The Android calendar screen SHALL replace its static week strip and manual month
navigation with `com.kizitonwose.calendar.view.CalendarView`.

### Scenario: Calendar screen renders CalendarView with month header

- **WHEN** `CalendarFragment` is displayed
- **THEN** the top bar SHALL remain unchanged
- **THEN** the screen SHALL show a programmatically updated month title above a
  horizontal `CalendarView`
- **THEN** the screen SHALL use dedicated day-cell and month-header layouts for
  date cells and weekday labels

### Scenario: Calendar screen supports scrolling across months

- **WHEN** the user scrolls the calendar horizontally
- **THEN** the calendar SHALL support a range from
  `YearMonth.now() - 100 months` through `YearMonth.now() + 100 months`
- **THEN** `monthScrollListener` SHALL update the visible month title and
  trigger loading of events for the visible month

## Requirement: Calendar screen displays host meeting indicators and selected date state

The Android calendar SHALL show host meetings as day indicators and maintain a
selected date for detailed event viewing.

### Scenario: Days with host meetings show event indicators

- **WHEN** the calendar has one or more host meetings within the visible month
- **THEN** `CalendarViewModel` SHALL expose a map of `LocalDate` to
  `List<CalendarEvent>` for that month
- **THEN** the day binder SHALL show an event dot for dates that have one or
  more mapped events

### Scenario: Selected day is visually highlighted

- **WHEN** the user taps an in-month day cell
- **THEN** the fragment SHALL update the selected date in `CalendarViewModel`
- **THEN** the tapped date SHALL render with the blue selected background used
  by the current design
- **THEN** the fragment SHALL call `notifyDateChanged()` for the old and new
  selected dates so recycled day cells render correctly

### Scenario: Out-of-month dates are not shown as normal selectable days

- **WHEN** a bound day belongs to the in-date or out-date portion of the month
- **THEN** the day binder SHALL render it as visually hidden or non-primary
  content according to the design choice for this screen
- **THEN** it SHALL NOT appear as the active selected day for the visible month

## Requirement: Calendar screen loads host meetings for month and date ranges

The Android calendar data layer SHALL retrieve host meetings from the existing
meetings API and expose both month-level indicators and selected-date event
details.

### Scenario: Month event load filters host meetings by requested date range

- **WHEN** `CalendarViewModel` requests events for a visible month
- **THEN** `CalendarRepository` SHALL call
  `MeetingsApi.listHostMeetings(100, null)`
- **THEN** the repository SHALL filter returned meetings to the requested
  `OffsetDateTime` range and map them to `CalendarEvent`

### Scenario: Selected date loads event list below the calendar

- **WHEN** a date is selected or the selected date changes
- **THEN** `CalendarViewModel` SHALL load events for that date range
- **THEN** the events section below the calendar SHALL display only events for
  the selected date
- **THEN** the empty state SHALL be shown when the selected date has no events

### Scenario: Calendar event domain model is fully populated

- **WHEN** a host meeting is mapped into a calendar event
- **THEN** the resulting `CalendarEvent` SHALL contain `id`, `title`,
  `startTime`, `endTime`, `status`, and `type`
- **THEN** the calendar UI SHALL use those fields for date dots and event detail
  rendering
