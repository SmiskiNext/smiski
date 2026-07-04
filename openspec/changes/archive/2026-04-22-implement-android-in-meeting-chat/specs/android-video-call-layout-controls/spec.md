# MODIFIED Requirements

## Requirement: Overflow actions reflect live badge and count state

The meeting actions bottom sheet SHALL expose current participant context while
keeping chat access behavior badge-free for the basic in-meeting chat scope.

### Scenario: Overflow actions reflect participant count without unread chat badge

- **WHEN** participant count changes during the call
- **THEN** the meeting actions bottom sheet SHALL render the latest participant
  count state for the Participants action row
- **THEN** the Chat action row SHALL remain accessible without unread badge or
  unread count indicators

### Scenario: Chat and Participants open their in-call surfaces

- **WHEN** the user selects Chat or Participants from the meeting actions bottom
  sheet
- **THEN** the selected in-call surface SHALL open without leaving
  `ActiveCallFragment`
- **THEN** chat availability SHALL follow active-meeting constraints from chat
  capability requirements
