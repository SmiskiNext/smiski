## ADDED Requirements

### Requirement: Web Meeting History list shows past meetings of the authenticated user

The web client SHALL render the authenticated user's past meetings (status
`ENDED` or `CANCELLED`) at `/{locale}/workspace/history` by calling
`listParticipatedMeetings(userId, pageSize, pageToken, status)` with
`status = "ENDED,CANCELLED"`. The user id MUST be obtained from `getMe()` before
the list call.

#### Scenario: Initial load shows the meeting list

- **WHEN** the user navigates to `/{locale}/workspace/history`
- **AND** `getMe()` returns a user id
- **AND** `listParticipatedMeetings` returns one or more meetings
- **THEN** the page SHALL render a card per returned meeting in the order
  returned by the API
- **AND** each card SHALL show the meeting title (or a localized "Untitled
  meeting" fallback when title is missing), a date+start-time line, a duration
  line, and a meeting-type badge ("Scheduled" or "Instant")
- **AND** the navigation bar SHALL show "History" as the active tab

#### Scenario: Cancelled meetings render with a distinct visual treatment

- **WHEN** a meeting in the list has status `CANCELLED`
- **THEN** the card title SHALL have a strikethrough text decoration
- **AND** the card SHALL render at 0.7 opacity
- **AND** a red "CANCELLED" badge SHALL appear next to the title
- **AND** the card's `aria-label` SHALL include the cancelled state so a screen
  reader announces it (for example, "{title} — cancelled")

#### Scenario: Empty state renders when the user has no past meetings

- **WHEN** `listParticipatedMeetings` returns an empty `content` array on the
  initial load
- **THEN** the page SHALL render an empty state with an illustrative icon, a
  short title, a description, and a primary action that navigates back to the
  workspace home so the user can start a new meeting
- **AND** the page SHALL NOT render any card placeholders

#### Scenario: Error state renders when the initial load fails

- **WHEN** `getMe()` or `listParticipatedMeetings` rejects on the initial load
- **THEN** the page SHALL render an error state with a message and a "Try again"
  button
- **AND** clicking "Try again" SHALL retry the initial load
- **AND** the page SHALL NOT show stale list content while the error state is
  visible

#### Scenario: Loading skeleton shows while initial data is fetched

- **WHEN** the initial fetch is pending and no data has loaded yet
- **THEN** the page SHALL render skeleton placeholder cards
- **AND** the page SHALL NOT render the empty state or the error state during
  loading

### Requirement: Web Meeting History supports cursor pagination via "Load more"

The web client SHALL paginate history results using the cursor returned by the
API (`nextPageToken`). A "Load more" button SHALL be the only mechanism for
advancing to the next page; the page SHALL NOT auto-fetch on scroll.

#### Scenario: Load more fetches the next page using the cursor

- **WHEN** the list is in a loaded state with a non-null `nextPageToken`
- **AND** the user clicks the "Load more" button at the end of the list
- **THEN** the page SHALL call `listParticipatedMeetings` with the current
  `nextPageToken` as `pageToken`
- **AND** on success, SHALL append the returned meetings to the existing list in
  API-returned order
- **AND** SHALL update `nextPageToken` from the response

#### Scenario: Load more button is hidden when no more pages exist

- **WHEN** the most recent successful response has `nextPageToken` of null,
  undefined, or empty string
- **THEN** the "Load more" button SHALL NOT be rendered

#### Scenario: Load more failure shows a toast and keeps the list

- **WHEN** a `loadMore` call rejects
- **THEN** the page SHALL keep the existing meetings visible
- **AND** SHALL show a sonner toast with an error message and a "Retry" action
- **AND** clicking "Retry" SHALL re-attempt the load with the same cursor

#### Scenario: Load more is disabled while a load is in flight

- **WHEN** a `loadMore` request is currently pending
- **THEN** the "Load more" button SHALL show a loading affordance and SHALL be
  disabled to prevent duplicate requests

### Requirement: Web Meeting History supports manual refresh via a button

The web client SHALL expose a "Refresh" button in the list section header that
re-fetches the first page without leaving the screen.

#### Scenario: Refresh re-fetches page one and replaces the list

- **WHEN** the user clicks the "Refresh" button while in any state other than
  the initial loading state
- **THEN** the page SHALL call `listParticipatedMeetings` with no `pageToken`
- **AND** on success, SHALL replace the displayed list with the new page and
  reset `nextPageToken` from the response

#### Scenario: Refresh keeps the list visible while in flight

- **WHEN** a refresh request is pending
- **THEN** the existing list SHALL stay mounted (with a reduced-opacity or
  loading affordance applied to the list, not removed)
- **AND** the "Refresh" button SHALL show a spinning state and be disabled for
  the duration of the request

#### Scenario: Refresh failure surfaces a toast and keeps the list

- **WHEN** a refresh request rejects
- **THEN** the page SHALL keep the previous list intact
- **AND** SHALL show a sonner error toast describing the failure

### Requirement: Web Meeting History opens detail when a card is clicked

#### Scenario: Card click navigates to the detail route

- **WHEN** the user clicks anywhere on a meeting card (or activates it via
  keyboard)
- **THEN** the web client SHALL navigate to
  `/{locale}/workspace/history/{meetingId}` using the Next.js client router

#### Scenario: Card is keyboard-activatable

- **WHEN** the card has keyboard focus
- **AND** the user presses Enter or Space
- **THEN** the navigation behaviour SHALL match a mouse click

### Requirement: Web Meeting History entry exists in the workspace navigation

#### Scenario: Workspace navigation includes a "History" tab

- **WHEN** the user is in any `/{locale}/workspace/*` page that uses
  `WorkspaceShell`
- **THEN** the navigation bar SHALL include a "History" item linking to
  `/{locale}/workspace/history`
- **AND** the "History" tab SHALL be marked as active when the current screen is
  the history list or detail
- **AND** the navigation label SHALL be localized in English ("History") and
  Vietnamese ("Lịch sử")

### Requirement: Web Meeting History uses session-expired error when the user is not authenticated

#### Scenario: getMe failure surfaces session-expired error

- **WHEN** the call to `getMe()` rejects with an authentication failure
- **THEN** the page SHALL render the error state with a localized "Please sign
  in again" style message
- **AND** SHALL NOT call `listParticipatedMeetings`
