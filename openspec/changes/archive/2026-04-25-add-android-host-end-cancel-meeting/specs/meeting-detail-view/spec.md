# MODIFIED Requirements

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
