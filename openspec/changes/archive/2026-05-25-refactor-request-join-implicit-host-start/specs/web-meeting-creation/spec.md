## MODIFIED Requirements

### Requirement: Web hosts can create and launch an instant meeting

The web app SHALL let a host start an instant meeting from shared
meeting-creation entry points by submitting validated meeting settings, creating
the meeting through the generated SDK, showing a shareable success view, and
handing the host off to the green-room route which performs the actual join.

#### Scenario: Instant meeting dialog opens from host entry points

- **WHEN** the host activates the web new-meeting action from a supported home
  or workspace surface
- **THEN** the system SHALL present a menu with exactly two actions: start an
  instant meeting and schedule for later
- **THEN** selecting the instant-meeting action SHALL open a dialog-based
  creation flow without leaving the current page

#### Scenario: Instant meeting request uses validated defaults and optional overrides

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

#### Scenario: Successful instant creation prepares a green-room handoff

- **WHEN** `createInstantMeeting` succeeds with a created meeting identifier and
  short code
- **THEN** the workflow SHALL transition directly from a `CREATING` state to a
  `READY` state without an intermediate start phase
- **THEN** the `READY` state SHALL include only the meeting identifier and short
  code returned by the create call
- **THEN** the system SHALL NOT call any separate start-meeting endpoint
- **THEN** the system SHALL NOT write meeting-room handoff credentials (token,
  room name, meeting identifier) to session storage on behalf of the instant
  flow

#### Scenario: Instant meeting success shows shareable confirmation before room entry

- **WHEN** the instant meeting workflow reaches its ready state
- **THEN** the dialog SHALL show a success view with the meeting short code and
  a copy-link action
- **THEN** activating the success view's continue action SHALL navigate the host
  to the locale-aware green-room route using the meeting short code as the
  `code` query parameter

#### Scenario: Instant meeting failure preserves retryable host feedback

- **WHEN** instant meeting creation fails due to validation, network, or server
  error
- **THEN** the dialog SHALL remain open on the current page
- **THEN** the system SHALL show inline error feedback consistent with the
  join-meeting error treatment
- **THEN** the workflow SHALL expose a retry path for retryable failures and a
  reset path for starting over
