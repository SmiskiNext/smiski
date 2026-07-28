# ui-design Specification

## Purpose

TBD - created by archiving change app-instant-meeting-antd-invitees. Update
Purpose after archive.

## Requirements

### Requirement: Create-instant surfaces use Ant Design components

The create-instant meeting UI SHALL be built from Ant Design components. The
shared instant-meeting modal SHALL use Ant Design `Modal`, `Form`, `Input`, and
`Select`, and SHALL be the single modal used by both the project-page dashboard
and the Issue Panel. The issue selector used by the meeting forms SHALL be an
Ant Design `Select` with search enabled, exposing the same selection contract it
does today so dependent forms continue to function.

#### Scenario: Shared instant modal renders with Ant Design

- **WHEN** a user opens the instant-meeting modal from the dashboard or the
  Issue Panel
- **THEN** the modal, its form fields, and its selectors are rendered using Ant
  Design components

#### Scenario: Issue selector remains usable by dependent forms

- **WHEN** the issue selector is rendered inside the schedule-meeting form after
  the conversion to Ant Design
- **THEN** selecting an issue reports the same chosen issue key upward and the
  schedule form behaves as before

### Requirement: Workspace-user invite picker

The instant-meeting modal SHALL offer an invite picker that lets the host select
multiple invitees by typing to search. The picker SHALL source suggestions from
Jira site (workspace) users and SHALL retain, for each selected invitee, the
`accountId`, `displayName`, and `email` needed to create the invitee. Typing
SHALL be debounced before a search is issued. The picker SHALL present distinct
loading, empty-result, and error states, and selected invitees SHALL remain
displayed with their identity even when they are not part of the current search
results.

#### Scenario: Type to search workspace users

- **WHEN** the host types a term into the invite picker
- **THEN** after debouncing, the picker shows matching workspace users to choose
  from

#### Scenario: Selected invitee persists across searches

- **WHEN** the host selects an invitee and then types a new search term that
  does not match that invitee
- **THEN** the previously selected invitee remains selected and displayed with
  its display name

#### Scenario: No matches shows an empty state

- **WHEN** a search returns no matching workspace users
- **THEN** the picker shows an empty-result state and no invitee is added

#### Scenario: Search failure shows an error without losing selection

- **WHEN** a workspace-user search fails
- **THEN** the picker shows an error state, keeps the already-selected invitees,
  and still allows submitting the form

### Requirement: Ant Design theming aligned to app color mode

The application SHALL configure Ant Design through a single theme provider so
Ant Design components adopt the app brand color and follow the app's active
light/dark color mode. When the resolved color mode is dark, Ant Design
components SHALL render with a dark theme; otherwise they SHALL render with a
light theme.

#### Scenario: Dark mode propagates to Ant Design

- **WHEN** the app's resolved color mode is dark
- **THEN** Ant Design components render using the dark theme

#### Scenario: Light mode propagates to Ant Design

- **WHEN** the app's resolved color mode is light
- **THEN** Ant Design components render using the light theme

### Requirement: Class-name utility follows the shadcn standard

The shared class-name utility (`cn`) SHALL merge conditional class values and
resolve conflicting Tailwind classes so the last conflicting class wins. It
SHALL accept the same range of inputs as the previous utility so existing call
sites keep compiling.

#### Scenario: Conflicting Tailwind classes resolve to the last

- **WHEN** `cn` is called with two conflicting Tailwind classes
- **THEN** the returned class string keeps only the last conflicting class

#### Scenario: Falsy inputs are ignored

- **WHEN** `cn` is called with `false`, `null`, or `undefined` among the inputs
- **THEN** those values are ignored and only truthy class names remain

### Requirement: Forge content security allowance for Ant Design styles

Because Ant Design injects styles at runtime, the app manifest SHALL declare the
content-security allowance required for inline styles so Ant Design renders
under Forge Custom UI. The manifest SHALL remain valid to the Forge linter after
the change.

#### Scenario: Manifest permits inline styles

- **WHEN** the app manifest is inspected after this change
- **THEN** it declares the content style allowance needed for Ant Design's
  runtime styles and passes Forge manifest validation

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
