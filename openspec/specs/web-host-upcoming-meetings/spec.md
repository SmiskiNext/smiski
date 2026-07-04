# ADDED Requirements

## Requirement: Web workspace home shows only upcoming host meetings

The web workspace home screen SHALL display upcoming host meetings derived from
the existing host meetings API by filtering and sorting the returned collection
on the client.

### Scenario: Home screen filters and sorts upcoming host meetings

- **WHEN** the workspace home screen loads host meetings from
  `listHostMeetings()`
- **THEN** the system SHALL keep only meetings whose `status` is `SCHEDULED` and
  whose `startTime` is after the current client time
- **THEN** the system SHALL sort the remaining meetings by `startTime` in
  ascending order before rendering

### Scenario: Home screen shows empty state after filtering

- **WHEN** the host meetings API returns no meetings that satisfy the upcoming
  filter
- **THEN** the system SHALL render the existing empty-state treatment for the
  upcoming meetings section
- **THEN** the system SHALL NOT render cards for past, live, ended, or cancelled
  meetings in that section

### Scenario: Home screen exposes loading and error states for upcoming meetings

- **WHEN** the upcoming meetings request is pending or fails
- **THEN** the system SHALL expose loading, success, empty, and error states
  through the existing discriminated-union UI state pattern
- **THEN** the upcoming meetings section SHALL render localized feedback
  appropriate to the current state

## Requirement: Web upcoming meeting cards show summary information and host actions

The web workspace SHALL render each upcoming host meeting as an actionable card
with summary metadata and host controls.

### Scenario: Card shows localized meeting summary content

- **WHEN** an upcoming host meeting card is rendered
- **THEN** the card SHALL display the meeting title or a localized untitled
  fallback when the title is missing
- **THEN** the card SHALL display the formatted date and time range, a status
  badge, the short code, and a description excerpt when a description exists
- **THEN** the description excerpt SHALL be visually truncated to a compact
  preview instead of showing the full body

### Scenario: Card primary action adapts to meeting status

- **WHEN** the host views an upcoming meeting card with a usable short code
- **THEN** the primary action SHALL be labeled `Start` for `SCHEDULED` meetings
  and `Join` for `LIVE` meetings
- **THEN** activating the primary action SHALL navigate to the locale-aware
  green-room route using the meeting short code as the `code` query parameter

### Scenario: Card supports copy, settings, and cancel actions

- **WHEN** the host uses the secondary actions on an upcoming meeting card
- **THEN** the system SHALL support copying the meeting short code with
  localized success feedback
- **THEN** the system SHALL open the existing meeting settings dialog for
  settings management
- **THEN** the system SHALL offer a cancel action that requires explicit
  confirmation before calling the existing cancel-meeting API

## Requirement: Web hosts can inspect upcoming meeting details from the home screen

The web workspace SHALL provide a detail surface for an upcoming host meeting
when the host selects the meeting card body.

### Scenario: Card click opens meeting detail view

- **WHEN** the host clicks or taps a meeting card outside of its nested action
  buttons
- **THEN** the system SHALL open a detail dialog or sheet for the selected
  meeting
- **THEN** nested action buttons SHALL NOT trigger the detail view when they are
  activated

### Scenario: Detail view shows complete meeting summary and settings

- **WHEN** the detail view is open for an upcoming host meeting
- **THEN** the system SHALL display the full meeting title, status badge, type
  badge, formatted full date and time range, full description when present, and
  copyable short code
- **THEN** the system SHALL display a read-only summary of available meeting
  settings from the meeting payload, including waiting room or admission policy,
  guest access, maximum participants, and comparable settings fields when
  present

### Scenario: Detail view reuses host actions consistently

- **WHEN** the host uses actions from the meeting detail view
- **THEN** the system SHALL provide the same start or join, copy link, settings,
  and cancel actions available on the meeting card
- **THEN** those actions SHALL behave consistently with the card-level actions
  and use the same localized labels and feedback

## Requirement: Web hosts can cancel an upcoming meeting from the home screen experience

The web workspace SHALL support cancelling an upcoming host meeting from the
card or detail experience and update the rendered list after success.

### Scenario: Cancellation requires explicit confirmation

- **WHEN** the host activates cancel for an upcoming meeting
- **THEN** the system SHALL show a confirmation dialog with localized title,
  message, confirm, and dismiss actions
- **THEN** the cancel-meeting API SHALL NOT be called unless the host confirms
  the action

### Scenario: Successful cancellation updates the upcoming list

- **WHEN** the host confirms cancellation and `cancelMeeting` succeeds
- **THEN** the system SHALL show localized success feedback
- **THEN** the cancelled meeting SHALL be removed from the current upcoming
  meetings list without requiring a full page reload

### Scenario: Cancellation failure preserves current data

- **WHEN** the cancellation request fails because of API, network, or server
  errors
- **THEN** the system SHALL keep the current upcoming meetings list visible
- **THEN** the system SHALL show localized error feedback and allow the host to
  retry or dismiss the dialog

## Requirement: Upcoming meeting copy and messaging are localized for supported web locales

The web upcoming meetings experience SHALL provide localized copy and action
feedback in supported locales.

### Scenario: English and Vietnamese strings cover new upcoming meeting surfaces

- **WHEN** the upcoming meetings list, card actions, detail view, and
  cancellation dialog are rendered in English or Vietnamese
- **THEN** all labels, button text, field captions, status-related action text,
  and empty or error messages SHALL come from locale message keys under the
  workspace home namespace
- **THEN** the localized keys SHALL cover untitled fallback text, copy-link
  messaging, cancellation success or failure feedback, and start or join error
  feedback
