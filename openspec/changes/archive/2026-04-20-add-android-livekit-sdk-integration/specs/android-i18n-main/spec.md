# MODIFIED Requirements

## Requirement: VideoCall Strings

All UI text in VideoCall flow SHALL use string resources.

### Scenario: PreJoin screen strings

- **WHEN** PreJoinFragment is displayed
- **THEN** meeting code label SHALL use `@string/prejoin_meeting_code`
- **THEN** display name label SHALL use `@string/prejoin_display_name`
- **THEN** join button SHALL use `@string/prejoin_join_button`
- **THEN** waiting-for-approval, denied, expired, and connection failure
  messages SHALL use string resources

### Scenario: ActiveCall strings

- **WHEN** ActiveCallFragment is displayed
- **THEN** end call button SHALL use `@string/call_end`
- **THEN** participants button SHALL use `@string/call_participants`
- **THEN** chat button SHALL use `@string/call_chat`
- **THEN** connection quality labels, participant tile labels, screen-share
  placeholder text, and unread chat badge content SHALL use string resources

## Requirement: Accessibility Strings

Content descriptions SHALL use string resources.

### Scenario: Icon content descriptions

- **WHEN** any icon needs content description
- **THEN** it SHALL use one of:
    - `@string/cd_avatar` for profile pictures
    - `@string/cd_settings` for settings icon
    - `@string/cd_search` for search icon
    - `@string/cd_previous_month` for calendar nav
    - `@string/cd_next_month` for calendar nav
    - `@string/cd_new_meeting` for new meeting action
    - `@string/cd_join_meeting` for join meeting action
    - `@string/cd_schedule_meeting` for schedule action
    - `@string/cd_mute_mic` / `@string/cd_unmute_mic` for mic toggle
    - `@string/cd_enable_camera` / `@string/cd_disable_camera` for camera toggle
    - `@string/cd_end_call` for end call button
    - dedicated string resources for connection quality, self-view preview,
      screen-share placeholder, camera flip, pin participant, and raise-hand
      actions when those controls are present

### Scenario: Video tile status descriptions are accessible

- **WHEN** participant video tiles display mic-muted, camera-off, or
  active-speaker state
- **THEN** those visual states SHALL have accessible text equivalents through
  content descriptions or announced labels sourced from string resources

## Requirement: Vietnamese Translations

All new strings SHALL have Vietnamese translations in `values-vi/strings.xml`.

### Scenario: Vietnamese translation parity

- **WHEN** a new string is added to `values/strings.xml`
- **THEN** a corresponding translation SHALL be added to `values-vi/strings.xml`

### Scenario: Video call additions are localized

- **WHEN** new strings are added for waiting-room approval, denial, expiration,
  connection quality, call controls, or participant tile states
- **THEN** Vietnamese translations SHALL be provided for each of those strings
