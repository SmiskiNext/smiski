# MODIFIED Requirements

## Requirement: VideoCall Strings

All UI text in VideoCall flow SHALL use string resources.

### Scenario: PreJoin screen strings

- **WHEN** PreJoinFragment is displayed
- **THEN** meeting code label SHALL use `@string/prejoin_meeting_code`
- **THEN** display name label SHALL use `@string/prejoin_display_name`
- **THEN** join button SHALL use `@string/prejoin_join_button`
- **THEN** password label, password hint, password helper text,
  password-required error, meeting-not-found error, and checking-state text
  SHALL use string resources
- **THEN** waiting-for-approval, denied, expired, and connection failure
  messages SHALL use string resources

### Scenario: ActiveCall strings

- **WHEN** ActiveCallFragment is displayed
- **THEN** end call button SHALL use `@string/call_end`
- **THEN** participants button SHALL use `@string/call_participants`
- **THEN** chat button SHALL use `@string/call_chat`
- **THEN** connection quality labels, participant tile labels, screen-share
  placeholder text, and unread chat badge content SHALL use string resources

## Requirement: Vietnamese Translations

All new strings SHALL have Vietnamese translations in `values-vi/strings.xml`.

### Scenario: Vietnamese translation parity

- **WHEN** a new string is added to `values/strings.xml`
- **THEN** a corresponding translation SHALL be added to `values-vi/strings.xml`

### Scenario: Video call additions are localized

- **WHEN** new strings are added for password-protected join, checking state,
  meeting lookup errors, waiting-room approval, denial, expiration, connection
  quality, call controls, or participant tile states
- **THEN** Vietnamese translations SHALL be provided for each of those strings
