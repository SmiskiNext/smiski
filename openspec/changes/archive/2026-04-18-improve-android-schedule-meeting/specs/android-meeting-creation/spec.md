# MODIFIED Requirements

## Requirement: Schedule meeting service integration

The Android app SHALL submit the schedule form to the backend meeting-management
API and react to the returned result.

### Scenario: Schedule request is built from validated form fields and selected settings

- **WHEN** the user taps the schedule submission button in `ScheduleFragment`
- **THEN** the system SHALL validate the schedule form before making the API
  request
- **THEN** the system SHALL treat the meeting title as optional, but if present
  SHALL enforce a maximum length of 255 characters
- **THEN** the system SHALL require date, time, and duration values before
  building the request
- **THEN** the system SHALL preserve the existing duration bounds of 15 through
  480 minutes
- **THEN** the system SHALL build a `MeetingManagementScheduleMeetingRequest`
  using the entered title, date, time, duration, and a settings object derived
  from the user's selected schedule settings
- **THEN** the settings object SHALL map the waiting-room toggle to
  `admissionPolicy`
- **THEN** the settings object SHALL include `muteOnEntry`, `maxParticipants`,
  `screenShareMode`, `chatEnabled`, `recordingEnabled`, and optional `password`
  values from the form
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

### Scenario: Schedule screen observes submission state and derived scheduling feedback

- **WHEN** `ScheduleFragment` triggers a schedule-meeting submission
- **THEN** `ScheduleViewModel` SHALL expose observable submission state
- **THEN** the fragment SHALL disable duplicate submissions while a request is
  in progress
- **THEN** the fragment SHALL react to success and error through observation
  rather than immediate inline navigation
- **THEN** the schedule presentation layer SHALL expose or derive calculated
  end-time feedback from the selected date, time, and duration when those inputs
  are valid

# ADDED Requirements

## Requirement: Schedule meeting form usability and accessibility

The Android schedule meeting form SHALL provide accessible, consistent, and
backend-aligned data entry for scheduling a meeting.

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

### Scenario: Schedule settings are organized for primary and advanced usage

- **WHEN** the schedule form is displayed
- **THEN** the primary settings area SHALL always show waiting room, host video,
  mute on entry, and password controls
- **THEN** the form SHALL provide a collapsible advanced settings section for
  max participants, screen share mode, chat enabled, and recording enabled
- **THEN** each settings row SHALL include an icon consistent with the app's
  create-meeting UI patterns

### Scenario: Submit action communicates in-progress state

- **WHEN** the user submits a valid schedule request
- **THEN** the schedule submit button SHALL show a loading indicator while the
  request is in progress
- **THEN** the button SHALL prevent duplicate taps until the request completes
