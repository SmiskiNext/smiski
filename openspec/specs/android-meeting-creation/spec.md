# Purpose

Define the Android host meeting-creation flows for dashboard entry, instant
meeting creation, scheduled meeting submission, and archive-status notes tied to
delivery verification.

# ADDED Requirements

## Requirement: Dashboard meeting creation FAB

The Android dashboard SHALL replace the existing "New Meeting" quick-action card
with a single Floating Action Button that exposes host actions from a popup
menu.

### Scenario: FAB is shown on dashboard

- **WHEN** `DashboardFragment` is displayed
- **THEN** the quick-actions row SHALL only show Join Meeting and Schedule cards
- **THEN** a Floating Action Button SHALL be visible at the bottom-right of the
  screen
- **THEN** the FAB SHALL have an accessible content description for starting a
  meeting

### Scenario: FAB opens popup menu

- **WHEN** the user taps the dashboard FAB
- **THEN** the system SHALL show a popup menu anchored to the FAB
- **THEN** the popup menu SHALL contain exactly two actions: "Start Instant
  Meeting" and "Schedule Meeting"

### Scenario: FAB schedule action opens schedule screen

- **WHEN** the user selects "Schedule Meeting" from the FAB popup menu
- **THEN** the system SHALL navigate to `ScheduleFragment`
- **THEN** the current dashboard screen SHALL remain on the back stack

## Requirement: Instant meeting creation from dashboard

The Android app SHALL create an instant meeting from the dashboard by calling
the backend meeting-management API with default host settings.

### Scenario: Create instant meeting successfully

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

### Scenario: Instant meeting success launches call flow

- **WHEN** `MeetingsApi.createInstantMeeting()` returns a successful
  `MeetingManagementMeetingResponse`
- **THEN** the system SHALL extract the created meeting ID and short code from
  the response
- **THEN** the system SHALL launch `VideoCallActivity`
- **THEN** the launch SHALL include the created short code so the call flow is
  prefilled for the new host session

### Scenario: Instant meeting failure shows localized feedback

- **WHEN** instant meeting creation fails due to validation, network, or server
  error
- **THEN** the system SHALL keep the user on `DashboardFragment`
- **THEN** the system SHALL show a Snackbar with a localized error message
- **THEN** the system SHALL clear any in-progress loading state after the error

## Requirement: Schedule meeting service integration

The Android app SHALL submit the schedule form to the backend meeting-management
API and react to the returned result using the simplified meeting settings
contract.

### Scenario: Schedule request is built from validated form fields and selected settings

- **WHEN** the user taps the schedule submission button in `ScheduleFragment`
- **THEN** the system SHALL validate the schedule form before making the API
  request
- **THEN** the system SHALL treat the meeting title as optional, but if present
  SHALL enforce a maximum length of 255 characters
- **THEN** the system SHALL require date, time, and duration values before
  building the API request
- **THEN** the system SHALL preserve the existing duration bounds of 15 through
  480 minutes
- **THEN** the system SHALL build a `MeetingManagementScheduleMeetingRequest`
  using the entered title, date, time, duration, and a settings object derived
  from the user's selected schedule settings
- **THEN** the settings object SHALL map the waiting-room toggle to
  `admissionPolicy`
- **THEN** the settings object SHALL include `allowGuest`, `maxParticipants`,
  `allowScreenShare`, `chatEnabled`, `allowMicrophone`, `allowVideo`, and
  optional `password` values from the form or defaults
- **THEN** the system SHALL read the saved host-video preference and apply it
  locally where supported by the Android flow
- **THEN** if the generated OpenAPI request model does not expose a host-video
  field, the Android app SHALL document that limitation and SHALL NOT fail the
  submission because the field cannot be sent
- **THEN** the request SHALL include a required settings object

### Scenario: Schedule meeting success returns to dashboard

- **WHEN** `MeetingsApi.scheduleMeeting()` returns a successful
  `MeetingManagementMeetingResponse`
- **THEN** the system SHALL show a success confirmation message
- **THEN** the system SHALL navigate back to `DashboardFragment`
- **THEN** the schedule form SHALL not remain in a submitting state

### Scenario: Schedule meeting failure shows localized feedback

- **WHEN** the schedule meeting API call fails
- **THEN** the system SHALL keep the user on `ScheduleFragment`
- **THEN** the system SHALL show a Snackbar with a localized error message
- **THEN** the user SHALL be able to edit the form and retry submission

## Requirement: Meeting creation presentation state

Meeting creation screens SHALL expose observable UI state so fragments can react
to loading, success, and error transitions without hardcoded navigation.

### Scenario: Dashboard observes instant-meeting state

- **WHEN** `DashboardFragment` triggers instant meeting creation
- **THEN** `DashboardViewModel` SHALL expose observable state for idle/loading
  and one-shot success/error events
- **THEN** `DashboardFragment` SHALL observe using `getViewLifecycleOwner()`

### Scenario: Schedule screen observes create or update submission state and derived scheduling feedback

- **WHEN** `ScheduleFragment` triggers a schedule-meeting creation or
  scheduled-meeting settings update
- **THEN** `ScheduleViewModel` SHALL expose observable submission state for the
  active mode
- **THEN** the fragment SHALL disable duplicate submissions while a request is
  in progress
- **THEN** the fragment SHALL react to success and error through observation
  rather than immediate inline navigation
- **THEN** the schedule presentation layer SHALL expose or derive calculated
  end-time feedback from the selected date, time, and duration when those inputs
  are valid

## Requirement: Schedule meeting form usability and accessibility

The Android schedule meeting form SHALL provide accessible, consistent, and
backend-aligned data entry for creating a meeting and reviewing or updating
pre-meeting settings.

### Scenario: Inline field validation is shown on blur

- **WHEN** the user leaves the title, date, time, duration, password, or
  max-participants fields after entering an invalid value
- **THEN** the corresponding `TextInputLayout` SHALL show an inline error state
  using the Android form error pattern
- **THEN** the inline error SHALL clear once the field value becomes valid

### Scenario: Date and time affordances are accessible and safe

- **WHEN** the schedule form is displayed
- **THEN** the date-picker trigger SHALL have an accessibility content
  description
- **THEN** the time-picker trigger SHALL have an accessibility content
  description
- **THEN** the date picker SHALL prevent selecting dates in the past

### Scenario: Duration selection shows derived end time

- **WHEN** the user selects a valid date, time, and duration combination
- **THEN** the schedule form SHALL show helper text indicating the calculated
  meeting end time
- **THEN** the helper text SHALL update whenever any of those inputs change

### Scenario: Schedule settings are organized for the simplified meeting contract

- **WHEN** the schedule form is displayed
- **THEN** the primary settings area SHALL always show waiting room, host video,
  password, microphone permission, and video permission controls
- **THEN** the form SHALL provide access to participant screen sharing, chat,
  guest access, and max participants controls without exposing removed settings
  such as mute on entry, recording enabled, or screen-share modes
- **THEN** each settings row SHALL include an icon consistent with the app's
  create-meeting UI patterns

### Scenario: Schedule form starts with backend-aligned defaults

- **WHEN** the user opens `ScheduleFragment` in create mode
- **THEN** the settings state SHALL default to `allowGuest=true`,
  `allowScreenShare=true`, `allowMicrophone=true`, `allowVideo=true`,
  `chatEnabled=true`, and `maxParticipants=100`

### Scenario: Edit mode communicates update context

- **WHEN** the user opens `ScheduleFragment` in edit mode for an existing
  scheduled meeting
- **THEN** the form SHALL render the current meeting values before submission
- **THEN** the primary action label SHALL clearly communicate that the user is
  updating an existing meeting

### Scenario: Submit action communicates in-progress state

- **WHEN** the user submits a valid create or update request
- **THEN** the schedule submit button SHALL show a loading indicator while the
  request is in progress
- **THEN** the button SHALL prevent duplicate taps until the request completes

## Requirement: ScheduleFragment supports upcoming-meeting edit mode

The Android schedule screen SHALL support an edit mode launched from upcoming
meeting cards so hosts can update pre-meeting settings before the meeting
starts.

### Scenario: Dashboard opens schedule edit mode for an upcoming meeting

- **WHEN** the user selects Edit Meeting from an upcoming meeting card menu
- **THEN** the system SHALL navigate to `ScheduleFragment` with the selected
  meeting identifier and an edit-mode flag
- **THEN** the fragment SHALL load the current meeting detail and meeting
  settings before enabling update submission

### Scenario: Edit mode prepopulates meeting data

- **WHEN** schedule edit mode loads successfully
- **THEN** the screen SHALL display the existing meeting title, scheduled
  date/time context, and current settings values
- **THEN** the primary call to action SHALL use the label "Update" instead of
  "Schedule"

### Scenario: Edit mode updates pre-meeting settings through the existing settings API

- **WHEN** the host submits valid changes from `ScheduleFragment` edit mode
- **THEN** the Android client SHALL validate the editable settings values before
  submitting
- **THEN** the client SHALL call the existing
  `PUT /api/v1/meetings/{id}/settings` flow for the selected scheduled meeting
- **THEN** a successful response SHALL return the user to `DashboardFragment`
  with refreshed upcoming meeting data

### Scenario: Edit mode failure keeps the user on the schedule screen

- **WHEN** the scheduled-meeting settings update fails
- **THEN** the system SHALL keep the user on `ScheduleFragment`
- **THEN** the screen SHALL clear its in-progress state
- **THEN** the UI SHALL show localized error feedback and allow retry

## Requirement: Archived delivery notes for meeting creation

The archived change record SHALL preserve the final delivery status discovered
during verification and release preparation.

### Scenario: Archive notes capture delivery status

- **WHEN** the change is prepared for archive
- **THEN** the implementation SHALL be recorded as complete
- **THEN** the archived notes SHALL record that build validation passed
- **THEN** the archived notes SHALL record that 2 CRITICAL issues were found and
  resolved during verification
- **THEN** the archived notes SHALL record that manual tasks 7.2 through 7.4 are
  still pending device testing
- **THEN** the archived notes SHALL record that host video preference is a
  documented backend API limitation because the field is not present in the
  generated OpenAPI schema
