# ADDED Requirements

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
  the meeting title, time range, icon, and a join action button

### Scenario: Dashboard shows empty state when no upcoming meetings exist

- **WHEN** the upcoming meetings request returns an empty list after filtering
- **THEN** the dashboard SHALL hide the meetings list
- **THEN** the dashboard SHALL show the existing empty-state layout

## Requirement: Dashboard exposes upcoming meetings UI state

The dashboard presentation layer SHALL expose observable loading, success,
empty, and error states for upcoming meetings using the existing `UiState<T>`
pattern.

### Scenario: Dashboard publishes loading state for upcoming meetings

- **WHEN** `DashboardViewModel` starts loading upcoming meetings
- **THEN** it SHALL publish `UiState.Loading<List<UpcomingMeeting>>`
- **THEN** the fragment SHALL observe the state with `getViewLifecycleOwner()`

### Scenario: Dashboard publishes success and empty states

- **WHEN** upcoming meetings loading completes successfully
- **THEN** `DashboardViewModel` SHALL publish
  `UiState.Success<List<UpcomingMeeting>>`
- **THEN** the fragment SHALL derive list-vs-empty rendering from the returned
  collection

### Scenario: Dashboard publishes error state

- **WHEN** loading upcoming meetings fails because of network, API, or mapping
  errors
- **THEN** `DashboardViewModel` SHALL publish
  `UiState.Error<List<UpcomingMeeting>>`
- **THEN** the fragment SHALL keep the user on the dashboard and show an error
  message instead of placeholder cards

## Requirement: Dashboard join action launches host video call flow

The Android dashboard SHALL allow the host to launch the existing video call
flow from an upcoming meeting item.

### Scenario: User joins an upcoming meeting from the dashboard list

- **WHEN** the user taps the join action on an upcoming meeting card
- **THEN** `DashboardFragment` SHALL launch `VideoCallActivity`
- **THEN** the launch intent SHALL include the meeting short code and host-mode
  extras consistent with the existing dashboard instant-meeting flow
