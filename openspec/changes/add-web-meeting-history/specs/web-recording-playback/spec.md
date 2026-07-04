## ADDED Requirements

### Requirement: Web recording overlay plays a recording in an inline HTML5 video player

The web client SHALL play meeting recordings via an in-page overlay containing a
native HTML5 `<video controls>` element. The overlay SHALL appear above the
detail content while leaving the underlying page mounted.

#### Scenario: Clicking a recording opens the overlay and starts playback

- **WHEN** the user clicks a recording row on the detail page
- **AND** the recording's `fileUrl` is a non-empty URL
- **THEN** the page SHALL render an overlay with a `<video>` element whose `src`
  is the recording URL
- **AND** the player SHALL show native browser controls

#### Scenario: Closing the overlay returns to the detail page

- **WHEN** the overlay is open
- **AND** the user clicks the close button
- **THEN** the overlay SHALL be removed from the DOM (or hidden) and the player
  SHALL stop playback
- **AND** focus SHALL return to the recording row that opened the overlay

#### Scenario: Empty recording URL shows the player error state

- **WHEN** the user clicks a recording whose `fileUrl` is missing, null, or
  empty
- **THEN** the overlay SHALL still open
- **AND** SHALL render an error message indicating the recording cannot be
  played
- **AND** SHALL render a "Retry" button

#### Scenario: Native video error event surfaces the player error state

- **WHEN** the `<video>` element fires an `error` event during load or playback
- **THEN** the overlay SHALL render the player error state with a "Retry" button
- **AND** the error state SHALL replace the player controls

#### Scenario: Retry re-attempts playback with the same URL

- **WHEN** the player error state is visible
- **AND** the user clicks "Retry"
- **THEN** the overlay SHALL re-mount or reload the `<video>` element with the
  same recording URL
- **AND** SHALL hide the error state if loading succeeds
