## ADDED Requirements

### Requirement: Schedule surface uses Ant Design components

The schedule-meeting create UI SHALL be built from Ant Design components. The
shared schedule-meeting modal SHALL use Ant Design `Modal`, `Form`, `Input`,
date/time selection controls, `Select`, and `Alert`, and SHALL be used by both
the project-page dashboard and the Issue Panel. The issue selector and the
workspace-user invite picker used by the schedule form SHALL be the same Ant
Design-based selectors used by the instant-meeting form.

#### Scenario: Shared schedule modal renders with Ant Design

- **WHEN** a user opens the schedule-meeting create form from the dashboard or
  the Issue Panel
- **THEN** the modal, its form fields, and its selectors are rendered using Ant
  Design components

#### Scenario: Legacy custom-kit controls are no longer used

- **WHEN** the schedule-meeting create form is rendered after this change
- **THEN** it no longer uses the legacy custom component kit or native
  `<form>`/`<input>` controls for its fields

### Requirement: Schedule form captures start, end, and time zone

The schedule-meeting create form SHALL let the host choose a start time, an end
time, and a time zone. The form SHALL reject submission unless a start time, an
end time, and a time zone are provided, unless the start time is strictly before
the end time, and unless the start time is not in the past relative to the
selected time zone. Rejections SHALL be shown inline and SHALL NOT issue a
create request.

#### Scenario: Valid start, end, and zone are accepted

- **WHEN** the host provides a start time before the end time, both not in the
  past for the selected zone, and submits
- **THEN** the form issues the scheduled-create request with the start time, end
  time, and zone

#### Scenario: End time not after start time is rejected

- **WHEN** the host provides a start time equal to or after the end time and
  submits
- **THEN** the form shows an inline validation error and issues no create
  request

#### Scenario: Missing required time fields are rejected

- **WHEN** the host submits without a start time, end time, or time zone
- **THEN** the form shows an inline validation error for the missing field and
  issues no create request

### Requirement: Backend errors shown in the schedule form

The schedule-meeting create form SHALL present backend problem responses (for
example validation errors or a start-time-in-past rejection) as an actionable
message and SHALL keep the modal open for correction.

#### Scenario: Backend validation error shown in the form

- **WHEN** the backend returns a problem response for a scheduled-create request
- **THEN** the form shows an error message describing the failure and the modal
  remains open for correction
