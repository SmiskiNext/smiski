# ADDED Requirements

## Requirement: Dashboard exposes host options for each upcoming meeting

The Android dashboard SHALL provide a per-meeting options menu for each upcoming
host meeting card.

### Scenario: Meeting options menu shows supported actions

- **WHEN** the user taps the more-options affordance on an upcoming meeting card
- **THEN** the system SHALL show a popup menu anchored to that card
- **THEN** the menu SHALL contain exactly these actions: Edit Meeting, Copy
  Link, Add to Calendar, and Cancel Meeting

### Scenario: Edit Meeting opens scheduled-meeting edit mode

- **WHEN** the user selects Edit Meeting from an upcoming meeting card menu
- **THEN** `DashboardFragment` SHALL navigate to `ScheduleFragment`
- **THEN** the navigation SHALL include the selected meeting identifier needed
  to load edit-mode data

### Scenario: Copy Link shares meeting join information

- **WHEN** the user selects Copy Link from an upcoming meeting card menu
- **THEN** the Android client SHALL copy a shareable join string derived from
  the meeting short code to the clipboard
- **THEN** the UI SHALL show confirmation feedback that the meeting link was
  copied

### Scenario: Add to Calendar launches platform calendar insert flow

- **WHEN** the user selects Add to Calendar from an upcoming meeting card menu
- **THEN** the Android client SHALL launch a calendar insert intent populated
  with the meeting title and scheduled time range
- **THEN** the user SHALL remain in control of saving the calendar event through
  the platform calendar app

### Scenario: Cancel Meeting removes the meeting from the upcoming list

- **WHEN** the user confirms Cancel Meeting from an upcoming meeting card menu
- **THEN** the Android client SHALL execute the existing cancel-meeting backend
  flow for the selected meeting
- **THEN** the dashboard SHALL refresh the upcoming meetings list after a
  successful cancellation
- **THEN** the cancelled meeting SHALL no longer appear in the upcoming host
  meetings list

# MODIFIED Requirements

## Requirement: Dashboard displays upcoming host meetings

The Android dashboard SHALL display a dynamic list of upcoming meetings hosted
by the current user instead of hardcoded placeholder cards.

### Scenario: Dashboard loads scheduled future meetings for the host

- **WHEN** `DashboardFragment` is displayed
- **THEN** `DashboardViewModel` SHALL request upcoming meetings through
  `GetUpcomingMeetingsUseCase`
- **THEN** the repository SHALL call `MeetingsApi.listHostMeetings(20, null)`,
  filter meetings to `status == SCHEDULED` with `startTime` in the future, map
  them to `UpcomingMeeting`, and sort them by `startTime` ascending

### Scenario: Dashboard shows upcoming meeting cards

- **WHEN** the upcoming meetings request returns one or more meetings
- **THEN** the dashboard SHALL hide the empty state include
- **THEN** the dashboard SHALL render a `RecyclerView` of meeting cards showing
  the meeting title, time range, icon, join action button, and a more-options
  affordance

### Scenario: Dashboard shows empty state when no upcoming meetings exist

- **WHEN** the upcoming meetings request returns an empty list after filtering
- **THEN** the dashboard SHALL hide the meetings list
- **THEN** the dashboard SHALL show the existing empty-state layout
