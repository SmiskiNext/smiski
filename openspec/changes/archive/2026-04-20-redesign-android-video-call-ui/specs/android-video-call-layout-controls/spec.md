# ADDED Requirements

## Requirement: Compact active-call controls use overflow actions

The Android active-call UI SHALL replace the crowded six-button primary control
bar with a compact primary control bar and a secondary overflow actions surface.

### Scenario: Primary call controls remain directly accessible

- **WHEN** `ActiveCallFragment` is displayed
- **THEN** it SHALL show primary controls for microphone, camera, end call, and
  more-actions in the floating control bar
- **THEN** each primary action SHALL preserve at least a 48dp touch target
- **THEN** screen sharing, chat, and participants SHALL no longer appear as
  separate primary-bar buttons

### Scenario: Overflow actions sheet exposes secondary meeting actions

- **WHEN** the user taps the more-actions control in `ActiveCallFragment`
- **THEN** the system SHALL show a modal meeting actions bottom sheet above the
  active call
- **THEN** the sheet SHALL expose Screen Share, Chat, Participants, and Change
  Layout actions
- **THEN** the sheet SHALL expose Settings only when the current participant is
  the meeting host

### Scenario: Overflow actions reflect live badge and count state

- **WHEN** unread chat state or participant count changes during the call
- **THEN** the meeting actions bottom sheet SHALL render the latest badge/count
  state for the affected action row
- **THEN** selecting Chat or Participants SHALL open the existing in-call
  surface without leaving `ActiveCallFragment`

## Requirement: Layout picker presents the supported video arrangements

The Android video-call UI SHALL provide a layout picker that lets the user
choose among the supported video arrangements for the current device.

### Scenario: Layout picker shows all available layout modes

- **WHEN** the user selects the layout entry point from the top bar or meeting
  actions sheet
- **THEN** the system SHALL show a layout picker bottom sheet
- **THEN** the picker SHALL present exactly four options: Auto, Tiled,
  Spotlight, and Sidebar
- **THEN** each option SHALL show an icon, label, and selected-state indicator

### Scenario: Layout picker reflects the current selection

- **WHEN** the layout picker is opened while a layout is already active
- **THEN** the currently selected layout SHALL be visually distinguished from
  the other options
- **THEN** dismissing and reopening the picker during the same call SHALL
  preserve the last selected layout state

## Requirement: Selected layout changes participant arrangement deterministically

The Android active-call UI SHALL apply the selected video layout consistently to
the participant surface.

### Scenario: Auto layout preserves dynamic participant balancing

- **WHEN** the selected layout is `AUTO`
- **THEN** the call surface SHALL use the existing dynamic span-count behavior
  that adapts to participant count
- **THEN** participant tiles SHALL continue to react to join, leave, and
  track-state changes without requiring manual refresh

### Scenario: Tiled layout uses a stable grid

- **WHEN** the selected layout is `TILED`
- **THEN** the call surface SHALL use a fixed two-column grid presentation for
  participant tiles
- **THEN** the grid SHALL remain deterministic as participant state changes

### Scenario: Spotlight and Sidebar layouts remain usable in phase 1

- **WHEN** the selected layout is `SPOTLIGHT` or `SIDEBAR`
- **THEN** the call surface SHALL apply a stable phase-1 arrangement or
  documented fallback that differs from `AUTO` selection state
- **THEN** the selected layout SHALL remain visible in the UI as the active
  choice
- **THEN** participant video tiles, self-view, and call controls SHALL remain
  functional while that layout is active
