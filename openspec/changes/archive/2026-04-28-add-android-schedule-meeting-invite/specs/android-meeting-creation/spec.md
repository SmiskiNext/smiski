# MODIFIED Requirements

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
  using the entered title, date, time, duration, invitees, and a settings object
  derived from the user's selected schedule settings
- **THEN** the request SHALL include invitees only in create mode and SHALL map
  each entered email to a `MeetingManagementInviteeRequest`
- **THEN** the invitee list SHALL be nullable and SHALL contain no more than 10
  entries from the Android client
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

### Scenario: Create mode provides invitee entry controls

- **WHEN** the user opens `ScheduleFragment` in create mode
- **THEN** the form SHALL display an "Invite Attendees" section after the
  duration selector and before the meeting settings header
- **THEN** the section SHALL include an email input, an add action, an invitee
  chip list, helper text showing the current count out of 10, and a divider that
  matches the existing schedule form pattern
- **THEN** invitee chips SHALL show the entered email and a remove action with
  an accessible content description

### Scenario: Invitee entry validates before adding

- **WHEN** the user taps the add action or triggers the Done IME action for an
  invitee email
- **THEN** the client SHALL validate the email format before adding it
- **THEN** the client SHALL reject duplicates using case-insensitive comparison
- **THEN** the client SHALL reject additions after 10 invitees have already been
  added
- **THEN** any validation failure SHALL be shown as an inline error on the
  invitee input
- **THEN** a successful add SHALL clear the invitee input value and its error

### Scenario: Invitee entry disables itself at the client cap

- **WHEN** the create-mode invitee list reaches 10 entries
- **THEN** the invitee email input SHALL be disabled
- **THEN** the add action SHALL be disabled
- **THEN** removing an invitee SHALL re-enable invitee entry

### Scenario: Edit mode communicates update context

- **WHEN** the user opens `ScheduleFragment` in edit mode for an existing
  scheduled meeting
- **THEN** the form SHALL render the current meeting values before submission
- **THEN** the primary action label SHALL clearly communicate that the user is
  updating an existing meeting
- **THEN** the invitee section SHALL be hidden or otherwise unavailable for
  editing

### Scenario: Submit action communicates in-progress state

- **WHEN** the user submits a valid create or update request
- **THEN** the schedule submit button SHALL show a loading indicator while the
  request is in progress
- **THEN** the button SHALL prevent duplicate taps until the request completes
