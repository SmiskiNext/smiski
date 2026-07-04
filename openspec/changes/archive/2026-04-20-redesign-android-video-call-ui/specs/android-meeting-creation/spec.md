# ADDED Requirements

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

# MODIFIED Requirements

## Requirement: Meeting creation presentation state

Meeting creation and scheduled-meeting edit screens SHALL expose observable UI
state so fragments can react to loading, success, and error transitions without
hardcoded navigation.

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
