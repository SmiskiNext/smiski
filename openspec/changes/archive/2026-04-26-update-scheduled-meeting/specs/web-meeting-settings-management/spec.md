# ADDED Requirements

## Requirement: Web hosts can edit meeting settings from active meeting surfaces

The web app SHALL let a host open a meeting settings dialog from the meeting
room toolbar or the workspace home meeting list, load the current meeting
settings from the backend, edit them through the shared settings form, and save
them through the existing meeting settings replacement API.

### Scenario: Host opens settings for the active meeting room

- **WHEN** the host activates the settings action from the meeting room toolbar
- **THEN** the system SHALL open a dialog for the current meeting identifier
  preserved in the room session credentials
- **THEN** the dialog SHALL call `getMeeting` for that meeting and map the
  returned settings into the shared `MeetingSettingsForm`
- **THEN** the dialog SHALL show a loading state until the current settings are
  ready

### Scenario: Host opens settings from the workspace home meeting list

- **WHEN** the host activates the settings action for a meeting item on the
  workspace home screen
- **THEN** the system SHALL open the same settings dialog for the selected
  meeting identifier
- **THEN** the dialog SHALL fetch the latest meeting settings before rendering
  editable values

### Scenario: Saving updated settings succeeds

- **WHEN** the host submits valid updated meeting settings from the dialog
- **THEN** the system SHALL map the form values into the backend request
  contract and call `putMeetingSettings({ path: { id }, body })`
- **THEN** the dialog SHALL show a saving state while the request is in progress
- **THEN** the dialog SHALL close after a successful save

### Scenario: Loading or saving meeting settings fails

- **WHEN** `getMeeting` or `putMeetingSettings` fails due to business,
  transport, or server errors
- **THEN** the dialog SHALL remain open on the current surface
- **THEN** the system SHALL show localized inline error feedback consistent with
  the web meeting creation flow
- **THEN** the host SHALL be able to retry without losing the current editing
  context

## Requirement: Web meeting settings forms use backend-aligned mapping semantics

The web app SHALL align its meeting settings mapping with the backend
replacement API so existing and updated meeting settings serialize and
deserialize consistently across create and edit flows.

### Scenario: Waiting room maps to backend admission policy values

- **WHEN** the web app serializes form values for meeting settings requests
- **THEN** `waitingRoom = true` SHALL map to `admissionPolicy = MANUAL_APPROVAL`
- **THEN** `waitingRoom = false` SHALL map to `admissionPolicy = ALLOW_ALL`

### Scenario: Existing backend settings map into form values

- **WHEN** the web app receives `MeetingManagementMeetingSettingsResponse` from
  `getMeeting`
- **THEN** `admissionPolicy = MANUAL_APPROVAL` SHALL map to `waitingRoom = true`
- **THEN** `admissionPolicy = ALLOW_ALL` SHALL map to `waitingRoom = false`
- **THEN** the remaining boolean settings SHALL map directly into their
  corresponding form fields
- **THEN** `requirePassword` SHALL determine whether the form marks password
  protection as enabled without pre-filling any stored password value

## Requirement: Workspace home shows real host meetings for settings management

The workspace home screen SHALL replace translation-backed mock upcoming
meetings with real host meeting summaries from `listHostMeetings` and SHALL
provide settings access for each listed meeting.

### Scenario: Host meetings load successfully on the home screen

- **WHEN** the workspace home screen mounts for an authenticated host
- **THEN** the system SHALL call `listHostMeetings`
- **THEN** the UI SHALL render each returned meeting with its title, start time,
  status, and a settings action

### Scenario: Home screen shows loading and empty states

- **WHEN** the host meetings request is in progress or returns no meetings
- **THEN** the screen SHALL show localized loading or empty-state copy instead
  of mock meeting cards

### Scenario: Home screen shows retryable load failure

- **WHEN** `listHostMeetings` fails
- **THEN** the screen SHALL show a localized error state for loading meetings
- **THEN** the host SHALL remain on the home screen and be able to retry by
  refreshing or revisiting the screen

## Requirement: Meeting settings copy is localized for supported web locales

The web app SHALL provide English and Vietnamese strings for the meeting
settings dialog and host-meeting loading states.

### Scenario: English locale renders meeting settings management copy

- **WHEN** the settings dialog or home meeting states render in the English
  locale
- **THEN** the title, save action, saving state, inline errors, loading state,
  empty state, and load-failure copy SHALL come from `en.json`

### Scenario: Vietnamese locale renders meeting settings management copy

- **WHEN** the settings dialog or home meeting states render in the Vietnamese
  locale
- **THEN** the title, save action, saving state, inline errors, loading state,
  empty state, and load-failure copy SHALL come from `vi.json`
