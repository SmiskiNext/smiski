## ADDED Requirements

### Requirement: Web Meeting Detail page renders for a single past meeting

The web client SHALL provide a dynamic route at
`/{locale}/workspace/history/{meetingId}` that loads a single meeting's full
detail by calling `getParticipatedMeetingDetail(userId, meetingId)`. The user id
MUST be obtained from `getMe()` before the detail call.

#### Scenario: Detail page loads and renders core meeting metadata

- **WHEN** the user navigates to `/{locale}/workspace/history/{meetingId}` for a
  meeting they participated in
- **AND** `getMe()` returns a user id
- **AND** `getParticipatedMeetingDetail` resolves with the detail
- **THEN** the page SHALL render the meeting title (or a localized "Untitled
  meeting" fallback if the title is missing)
- **AND** SHALL render a meeting-type badge ("Scheduled" or "Instant") when
  `type` is present
- **AND** SHALL render a meeting-status badge ("Scheduled", "Live", "Ended", or
  "Cancelled") when `status` is present
- **AND** SHALL render the meeting's full date and the start–end time range with
  a duration suffix when both `startTime` and `endTime` are present
- **AND** SHALL render the start time only ("starts at HH:MM") when `endTime` is
  missing

#### Scenario: Loading state renders while the detail is fetched

- **WHEN** the detail fetch is pending
- **THEN** the page SHALL render a loading affordance (spinner or skeleton)
- **AND** SHALL NOT render the success or error layouts

#### Scenario: Error state renders with retry when the detail fetch fails

- **WHEN** `getMe()` or `getParticipatedMeetingDetail` rejects
- **THEN** the page SHALL render an error state with a message and a "Try again"
  button
- **AND** clicking "Try again" SHALL retry the detail fetch

#### Scenario: Back navigation returns to the history list

- **WHEN** the user activates the page's back affordance
- **THEN** the page SHALL navigate back to `/{locale}/workspace/history`

### Requirement: Web Meeting Detail page hides empty optional sections

#### Scenario: Description section is hidden when description is empty

- **WHEN** the loaded detail has no description, an empty description, or a
  whitespace-only description
- **THEN** the description section SHALL NOT render

#### Scenario: Recordings section is hidden when there are no recordings

- **WHEN** the loaded detail has no `recordings` field, or the field is an empty
  list
- **THEN** the recordings section SHALL NOT render

#### Scenario: Participants section is hidden when there are no participants

- **WHEN** the loaded detail has no `participants` field, or the field is an
  empty list
- **THEN** the participants section SHALL NOT render

### Requirement: Web Meeting Detail participants section previews the first five and expands on demand

#### Scenario: Five or fewer participants render in full immediately

- **WHEN** the loaded detail has between one and five participants
- **THEN** the page SHALL render every participant in the section
- **AND** SHALL NOT render an expand button

#### Scenario: More than five participants render a preview with an expand button

- **WHEN** the loaded detail has more than five participants
- **THEN** the page SHALL render the first five participants by default
- **AND** SHALL render an expand button labelled with the number of hidden
  participants (for example, "Show {N} more")
- **AND** the section header SHALL include the total participant count

#### Scenario: Expanding the participants list reveals the rest

- **WHEN** the user clicks the expand button
- **THEN** the section SHALL render every participant in the list
- **AND** the expand button SHALL be removed from the layout

#### Scenario: Each participant row renders display name and role

- **WHEN** a participant is rendered
- **THEN** the row SHALL show the participant's display name (or an empty string
  if missing)
- **AND** SHALL show an avatar based on the participant's display-name initials
  when no avatar URL is provided
- **AND** SHALL show a role badge ("Host", "Participant", or "Guest") when
  `role` is present

### Requirement: Web Meeting Detail recordings section lists recordings in order

#### Scenario: Each recording row renders index, date, and duration

- **WHEN** the loaded detail contains one or more recordings
- **THEN** the page SHALL render each recording as a row showing a one-based
  index label (for example, "Recording 1"), a creation-date subtitle (when
  present), and a formatted duration in MM:SS or HH:MM:SS
- **AND** the section header SHALL include the recording count

#### Scenario: Recording row shows a play affordance

- **WHEN** a recording row is rendered
- **THEN** the row SHALL be a clickable, keyboard-activatable element that
  triggers the playback overlay
