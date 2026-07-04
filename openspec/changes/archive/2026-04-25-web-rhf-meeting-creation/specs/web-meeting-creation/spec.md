# ADDED Requirements

## Requirement: Web hosts can create and launch an instant meeting

The web app SHALL let a host start an instant meeting from shared
meeting-creation entry points by submitting validated meeting settings, creating
the meeting through the generated SDK, starting it automatically, and handing
the host off to the meeting room.

### Scenario: Instant meeting dialog opens from host entry points

- **WHEN** the host activates the web new-meeting action from a supported home
  or workspace surface
- **THEN** the system SHALL present a menu with exactly two actions: start an
  instant meeting and schedule for later
- **THEN** selecting the instant-meeting action SHALL open a dialog-based
  creation flow without leaving the current page

### Scenario: Instant meeting request uses validated defaults and optional overrides

- **WHEN** the host submits the instant meeting dialog with valid form data
- **THEN** the system SHALL validate the form with a schema that accepts an
  optional title and a required meeting-settings object
- **THEN** the meeting settings SHALL include `admissionPolicy`, `allowGuest`,
  `maxParticipants`, `allowScreenShare`, `chatEnabled`, `allowMicrophone`,
  `allowVideo`, and optional `password`
- **THEN** the system SHALL map the waiting-room control to the request
  `admissionPolicy`
- **THEN** the system SHALL call `createInstantMeeting` with the validated
  request payload

### Scenario: Successful instant creation starts the meeting and prepares redirect state

- **WHEN** `createInstantMeeting` succeeds with a created meeting identifier and
  short code
- **THEN** the system SHALL automatically call `startMeeting` for the created
  meeting before redirecting the host
- **THEN** the workflow SHALL transition through explicit loading states until
  meeting-room handoff data is ready
- **THEN** the ready state SHALL include the meeting identifier, short code, and
  meeting-room launch data needed by the web client

### Scenario: Instant meeting success shows shareable confirmation before room entry

- **WHEN** the instant meeting workflow reaches its ready state
- **THEN** the dialog SHALL show a success view with the meeting short code and
  a copy-link action
- **THEN** the system SHALL automatically navigate the host to
  `/workspace/meeting-room` after the success handoff interval completes

### Scenario: Instant meeting failure preserves retryable host feedback

- **WHEN** instant meeting creation or meeting start fails due to validation,
  network, or server error
- **THEN** the dialog SHALL remain open on the current page
- **THEN** the system SHALL show inline error feedback consistent with the
  join-meeting error treatment
- **THEN** the workflow SHALL expose a retry path for retryable failures and a
  reset path for starting over

## Requirement: Web hosts can schedule a meeting from the workspace schedule page

The web app SHALL convert the existing workspace schedule screen into a
validated schedule-meeting flow that submits invitees and meeting settings
through the generated SDK and confirms success in-place.

### Scenario: Schedule page validates required scheduling inputs

- **WHEN** the host submits the workspace schedule form
- **THEN** the system SHALL validate title, description, date, time, duration,
  invitees, and meeting settings with a schema-based form
- **THEN** the form SHALL require date, time, and duration values
- **THEN** the computed meeting start time SHALL be in the future
- **THEN** invitees, when present, SHALL each be valid email addresses

### Scenario: Schedule request maps form values into backend contract

- **WHEN** the schedule form contains valid values
- **THEN** the system SHALL compute `startTime` from the selected date and time
- **THEN** the system SHALL compute `endTime` from `startTime` plus the selected
  duration
- **THEN** the system SHALL build a `MeetingManagementScheduleMeetingRequest`
  with optional title and description, computed start and end times, validated
  settings, and optional invitees
- **THEN** the system SHALL call `scheduleMeeting` with the mapped request

### Scenario: Schedule success shows confirmation details

- **WHEN** `scheduleMeeting` returns a successful meeting response
- **THEN** the schedule screen SHALL leave its submitting state
- **THEN** the system SHALL show a success confirmation dialog with the meeting
  code and scheduled date/time details
- **THEN** the success flow SHALL provide a completion action without requiring
  a page refresh

### Scenario: Schedule failure keeps the form editable

- **WHEN** the schedule meeting API call fails
- **THEN** the system SHALL keep the host on the workspace schedule page
- **THEN** the form SHALL clear its submitting state
- **THEN** the system SHALL show inline error feedback while preserving the
  entered values for correction and retry

## Requirement: Shared meeting settings and invitee inputs are reusable across web meeting-creation flows

The web app SHALL provide reusable meeting-settings and invitee form controls so
instant and scheduled meeting creation share the same validation rules,
accessibility behavior, and localized copy.

### Scenario: Shared settings component renders backend-aligned meeting controls

- **WHEN** the meeting settings section is rendered in an instant or scheduled
  meeting form
- **THEN** the UI SHALL expose controls for waiting room, guest access, screen
  sharing, chat, microphone, video, max participants, and optional password
- **THEN** those controls SHALL bind through the shared RHF form infrastructure
  rather than local component state
- **THEN** default values SHALL align with the shared meeting settings schema
  used by both flows

### Scenario: Invitee input manages validated email chips

- **WHEN** the host enters invitee email addresses on the schedule page
- **THEN** pressing Enter on a valid email SHALL add it as a removable chip in
  the form state
- **THEN** invalid email text SHALL NOT be added as a chip
- **THEN** each chip remove action SHALL expose an accessible label that
  identifies the email being removed

### Scenario: Meeting-creation strings are localized in supported web locales

- **WHEN** the meeting-creation flows are rendered in English or Vietnamese
- **THEN** the instant-meeting dialog, shared settings section, new-meeting
  menu, schedule form, success messages, and user-visible errors SHALL read from
  localized translation keys for that locale
