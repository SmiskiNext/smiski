## ADDED Requirements

### Requirement: Dashboard meeting creation FAB

The Android dashboard SHALL replace the existing "New Meeting" quick-action card
with a single Floating Action Button that exposes host actions from a popup
menu.

#### Scenario: FAB is shown on dashboard

- **WHEN** `DashboardFragment` is displayed
- **THEN** the quick-actions row SHALL only show Join Meeting and Schedule cards
- **THEN** a Floating Action Button SHALL be visible at the bottom-right of the
  screen
- **THEN** the FAB SHALL have an accessible content description for starting a
  meeting

#### Scenario: FAB opens popup menu

- **WHEN** the user taps the dashboard FAB
- **THEN** the system SHALL show a popup menu anchored to the FAB
- **THEN** the popup menu SHALL contain exactly two actions: "Start Instant
  Meeting" and "Schedule Meeting"

#### Scenario: FAB schedule action opens schedule screen

- **WHEN** the user selects "Schedule Meeting" from the FAB popup menu
- **THEN** the system SHALL navigate to `ScheduleFragment`
- **THEN** the current dashboard screen SHALL remain on the back stack

### Requirement: Instant meeting creation from dashboard

The Android app SHALL create an instant meeting from the dashboard by calling
the backend meeting-management API with default host settings.

#### Scenario: Create instant meeting successfully

- **WHEN** the user selects "Start Instant Meeting" from the dashboard FAB menu
- **THEN** the system SHALL build a
  `MeetingManagementCreateInstantMeetingRequest`
- **THEN** the request SHALL include a required settings object with waiting
  room enabled by default
- **THEN** the system SHALL read the user's saved host-video preference before
  constructing the request
- **THEN** the system SHALL preserve that preference for local meeting-launch
  behavior
- **THEN** if the generated OpenAPI request model does not expose a host-video
  field, the Android app SHALL omit that field from the request and treat it as
  a documented backend schema limitation
- **THEN** the system SHALL call `MeetingsApi.createInstantMeeting()`

#### Scenario: Instant meeting success launches call flow

- **WHEN** `MeetingsApi.createInstantMeeting()` returns a successful
  `MeetingManagementMeetingResponse`
- **THEN** the system SHALL extract the created meeting ID and short code from
  the response
- **THEN** the system SHALL launch `VideoCallActivity`
- **THEN** the launch SHALL include the created short code so the call flow is
  prefilled for the new host session

#### Scenario: Instant meeting failure shows localized feedback

- **WHEN** instant meeting creation fails due to validation, network, or server
  error
- **THEN** the system SHALL keep the user on `DashboardFragment`
- **THEN** the system SHALL show a Snackbar with a localized error message
- **THEN** the system SHALL clear any in-progress loading state after the error

### Requirement: Schedule meeting service integration

The Android app SHALL submit the schedule form to the backend meeting-management
API and react to the returned result.

#### Scenario: Schedule request is built from form fields

- **WHEN** the user taps the schedule submission button in `ScheduleFragment`
- **THEN** the system SHALL validate required form fields before making the API
  request
- **THEN** the system SHALL build a `MeetingManagementScheduleMeetingRequest`
  using the entered topic, date, time, duration, and waiting-room setting
- **THEN** the system SHALL read the saved host-video preference and apply it
  locally where supported by the Android flow
- **THEN** if the generated OpenAPI request model does not expose a host-video
  field, the Android app SHALL document that limitation and SHALL NOT fail the
  submission because the field cannot be sent
- **THEN** the request SHALL include a required settings object

#### Scenario: Verification findings are reflected in delivery status

- **WHEN** the change is prepared for archive
- **THEN** the implementation SHALL be recorded as complete
- **THEN** the archived notes SHALL record that build validation passed
- **THEN** the archived notes SHALL record that 2 CRITICAL issues were found and
  resolved during verification
- **THEN** the archived notes SHALL record that manual tasks 7.2 through 7.4 are
  still pending device testing

#### Scenario: Schedule meeting success returns to dashboard

- **WHEN** `MeetingsApi.scheduleMeeting()` returns a successful
  `MeetingManagementMeetingResponse`
- **THEN** the system SHALL show a success confirmation message
- **THEN** the system SHALL navigate back to `DashboardFragment`
- **THEN** the schedule form SHALL not remain in a submitting state

#### Scenario: Schedule meeting failure shows localized feedback

- **WHEN** the schedule meeting API call fails
- **THEN** the system SHALL keep the user on `ScheduleFragment`
- **THEN** the system SHALL show a Snackbar with a localized error message
- **THEN** the user SHALL be able to edit the form and retry submission

### Requirement: Meeting creation presentation state

Meeting creation screens SHALL expose observable UI state so fragments can react
to loading, success, and error transitions without hardcoded navigation.

#### Scenario: Dashboard observes instant-meeting state

- **WHEN** `DashboardFragment` triggers instant meeting creation
- **THEN** `DashboardViewModel` SHALL expose observable state for idle/loading
  and one-shot success/error events
- **THEN** `DashboardFragment` SHALL observe using `getViewLifecycleOwner()`

#### Scenario: Schedule screen observes submission state

- **WHEN** `ScheduleFragment` triggers a schedule-meeting submission
- **THEN** `ScheduleViewModel` SHALL expose observable submission state
- **THEN** the fragment SHALL disable duplicate submissions while a request is
  in progress
- **THEN** the fragment SHALL react to success and error through observation
  rather than immediate inline navigation
