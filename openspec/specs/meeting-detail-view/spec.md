# ADDED Requirements

## Requirement: Display meeting detail header

The system SHALL display meeting header information with title and status
badges.

### Scenario: Meeting detail header display

- **WHEN** user views meeting detail screen
- **THEN** system SHALL display the meeting title (or "Untitled Meeting" if
  null)
- **AND** system SHALL display type badge (INSTANT or SCHEDULED)
- **AND** system SHALL display status badge (ENDED or CANCELLED)

## Requirement: Display meeting time information

The system SHALL display comprehensive time information for the meeting.

### Scenario: Time info for completed meeting

- **WHEN** meeting has both startTime and endTime
- **THEN** system SHALL display full date (e.g., "Wednesday, April 16, 2026")
- **AND** system SHALL display time range (e.g., "2:30 PM - 3:15 PM")
- **AND** system SHALL display calculated duration (e.g., "45 min")

### Scenario: Time info for cancelled meeting without endTime

- **WHEN** meeting has status CANCELLED and endTime is null
- **THEN** system SHALL display full date
- **AND** system SHALL display start time only
- **AND** system SHALL display duration as "—"

## Requirement: Display meeting description

The system SHALL display the meeting description when available.

### Scenario: Description is present

- **WHEN** meeting has a non-empty description
- **THEN** system SHALL display "Description" section header
- **AND** system SHALL display the description text

### Scenario: Description is empty or null

- **WHEN** meeting has no description (null or empty string)
- **THEN** system SHALL NOT display the Description section

## Requirement: Display participants list

The system SHALL display the list of meeting participants with their roles.

### Scenario: Participants display

- **WHEN** meeting has participants
- **THEN** system SHALL display "Participants (N)" section header where N is
  total count
- **AND** system SHALL display each participant with avatar (or initials
  fallback), display name, and role badge (HOST/PARTICIPANT/GUEST)

### Scenario: Participant avatar fallback

- **WHEN** participant has no avatar URL
- **THEN** system SHALL display InitialsDrawable with participant's display name
  initials
- **AND** background color SHALL be deterministic based on participant's userId

### Scenario: Large participant list

- **WHEN** meeting has more than 5 participants
- **THEN** system SHALL display first 5 participants
- **AND** system SHALL display "+ N more" expandable link
- **WHEN** user taps "+ N more"
- **THEN** system SHALL expand to show all participants

## Requirement: Display recordings section

The system SHALL display available recordings for the meeting.

### Scenario: Recordings available

- **WHEN** meeting has one or more recordings
- **THEN** system SHALL display "Recordings (N)" section header
- **AND** each recording item SHALL show play icon, recording label, and
  duration

### Scenario: No recordings

- **WHEN** meeting has no recordings (empty array)
- **THEN** system SHALL NOT display the Recordings section

## Requirement: Navigate from meeting history list

The system SHALL allow navigation from the list to detail view.

### Scenario: Navigate to meeting detail

- **WHEN** user taps a meeting item in the history list
- **THEN** system SHALL navigate to MeetingDetailFragment
- **AND** system SHALL pass the meetingId as navigation argument

### Scenario: Navigate back to list

- **WHEN** user taps back button in MeetingDetailFragment
- **THEN** system SHALL navigate back to MeetingHistoryFragment
- **AND** previous scroll position and loaded data SHALL be preserved

## Requirement: Loading and error states

The system SHALL handle loading and error states for meeting detail.

### Scenario: Loading meeting detail

- **WHEN** meeting detail is being fetched
- **THEN** system SHALL display a centered ProgressBar

### Scenario: Failed to load meeting detail

- **WHEN** meeting detail API call fails
- **THEN** system SHALL display inline error message
- **AND** system SHALL display "Retry" button

## Requirement: Host cancel action in schedule edit view

The ScheduleFragment in edit mode SHALL expose a host-only "Cancel Meeting"
button when the loaded meeting has SCHEDULED status. The button SHALL be hidden
from non-hosts and for meetings in any other status (LIVE, ENDED, CANCELLED). On
successful cancellation, the screen SHALL navigate back and trigger a dashboard
refresh.

### Scenario: Cancel button visible for host on scheduled meeting

- **WHEN** the ScheduleFragment loads in edit mode and the current user is the
  meeting host and the meeting status is SCHEDULED
- **THEN** the system SHALL display a "Cancel Meeting" button

### Scenario: Cancel button hidden for non-scheduled or non-host

- **WHEN** the ScheduleFragment loads in edit mode and either the current user
  is not the host OR the meeting status is not SCHEDULED
- **THEN** the system SHALL NOT display the "Cancel Meeting" button

### Scenario: Navigate back after successful cancel

- **WHEN** a host successfully cancels a scheduled meeting from the
  ScheduleFragment edit view
- **THEN** the system SHALL pop the back stack to the dashboard and the
  dashboard SHALL reload its upcoming meetings list
