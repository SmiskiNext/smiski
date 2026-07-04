# ADDED Requirements

## Requirement: Display meeting history list

The system SHALL display a paginated list of meetings the user has participated
in, filtered to show only ENDED and CANCELLED status meetings.

### Scenario: Initial load shows meetings

- **WHEN** user navigates to Meeting History screen
- **THEN** system SHALL display a list of past meetings ordered by startTime
  descending (most recent first)
- **AND** each list item SHALL show: title (or "Untitled Meeting" if null),
  date/time, duration, type badge (INSTANT/SCHEDULED)

### Scenario: Cancelled meetings display with visual distinction

- **WHEN** a meeting in the list has status CANCELLED
- **THEN** system SHALL display the title with strikethrough text decoration
- **AND** system SHALL display the item with 0.7 opacity
- **AND** system SHALL display a red "CANCELLED" badge
- **AND** duration SHALL display as "—" if endTime is null

### Scenario: Skeleton loading on initial load

- **WHEN** meeting history data is being fetched for the first time
- **THEN** system SHALL display skeleton placeholder items (5 items)
- **AND** system SHALL NOT display empty state or error state during loading

## Requirement: Infinite scroll pagination

The system SHALL load more meetings when user scrolls near the end of the list.

### Scenario: Load more meetings on scroll

- **WHEN** user scrolls within 3 items of the end of the list
- **AND** there are more pages available (hasNext = true)
- **THEN** system SHALL display a loading indicator at the bottom
- **AND** system SHALL fetch the next page using nextPageToken
- **AND** system SHALL append new items to the existing list

### Scenario: End of list reached

- **WHEN** user has scrolled to load all available pages (hasNext = false)
- **THEN** system SHALL NOT display loading indicator
- **AND** system SHALL NOT make additional API calls on further scrolling

## Requirement: Pull to refresh

The system SHALL allow users to refresh the meeting history by pulling down.

### Scenario: Refresh meeting list

- **WHEN** user pulls down on the list
- **THEN** system SHALL display SwipeRefreshLayout indicator
- **AND** system SHALL fetch meeting history from the beginning (no pageToken)
- **AND** system SHALL replace the entire list with fresh data on success

## Requirement: Empty state display

The system SHALL display an empty state when the user has no meeting history.

### Scenario: No meetings to display

- **WHEN** meeting history API returns empty list
- **THEN** system SHALL display an illustration
- **AND** system SHALL display "No Meeting History Yet" title
- **AND** system SHALL display "Your past meetings will appear here once you've
  hosted or joined one." subtitle
- **AND** system SHALL display "Start a Meeting" action button

## Requirement: Error state with retry

The system SHALL display an error state when meeting history fails to load.

### Scenario: Initial load fails

- **WHEN** meeting history API call fails (network error or server error)
- **THEN** system SHALL display an error illustration
- **AND** system SHALL display "Something Went Wrong" title
- **AND** system SHALL display "We couldn't load your meeting history. Please
  try again." subtitle
- **AND** system SHALL display "Retry" button

### Scenario: Retry after error

- **WHEN** user taps "Retry" button
- **THEN** system SHALL display skeleton loading state
- **AND** system SHALL retry the API call

### Scenario: Pagination load fails

- **WHEN** loading more meetings (pagination) fails
- **THEN** system SHALL display a Snackbar with error message and "Retry" action
- **AND** system SHALL NOT remove already loaded items

## Requirement: Navigation from profile

The system SHALL allow users to access meeting history from the profile screen.

### Scenario: Navigate to meeting history

- **WHEN** user taps "Meeting History" menu item in ProfileFragment
- **THEN** system SHALL navigate to MeetingHistoryFragment
- **AND** system SHALL hide bottom navigation bar

### Scenario: Navigate back to profile

- **WHEN** user taps back button in MeetingHistoryFragment
- **THEN** system SHALL navigate back to ProfileFragment
- **AND** system SHALL show bottom navigation bar
