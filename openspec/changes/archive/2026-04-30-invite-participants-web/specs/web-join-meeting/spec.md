# MODIFIED Requirements

## Requirement: Guest opens a public join link

The web app SHALL render the shared join-meeting flow in guest mode for
`/{locale}/join/{code}` routes. The guest join page SHALL also extract an
optional `token` search parameter and pass it to `JoinMeetingContainer` as
`inviteToken`.

### Scenario: Guest opens a public join link without an invite token

- **WHEN** an unauthenticated user navigates to `/{locale}/join/{code}` without
  a `?token=` query parameter
- **THEN** the route SHALL remain accessible without workspace authentication
  middleware blocking it
- **THEN** the system SHALL render `JoinMeetingContainer` in guest mode with
  `inviteToken` set to `undefined`
- **THEN** the meeting code field SHALL be prefilled from the route parameter
- **THEN** the guest SHALL be required to enter a display name before join
  submission

### Scenario: Guest opens a public join link with an invite token

- **WHEN** an unauthenticated user navigates to
  `/{locale}/join/{code}?token=RAW_TOKEN`
- **THEN** the route SHALL remain accessible without workspace authentication
  middleware blocking it
- **THEN** the page component SHALL extract `RAW_TOKEN` from search params and
  pass it as `inviteToken` to `JoinMeetingContainer`
- **THEN** `JoinMeetingContainer` SHALL validate the token before allowing form
  interaction (see `web-invite-token-join` spec)

## Requirement: Join-meeting copy is localized for supported web locales

The web app SHALL provide localized join-meeting UI strings in English and
Vietnamese for form labels, button text, waiting-room copy, user-visible errors,
invite-section labels, and invite-token feedback messages.

### Scenario: English locale renders join-meeting copy

- **WHEN** the join-meeting flow is rendered with the English locale
- **THEN** the page title, meeting code label, display-name label, password
  label, join action, waiting-room text, error messages, and all invite-token
  status and error strings SHALL come from the `joinMeeting` translation
  namespace in `en.json`

### Scenario: Vietnamese locale renders join-meeting copy

- **WHEN** the join-meeting flow is rendered with the Vietnamese locale
- **THEN** the page title, meeting code label, display-name label, password
  label, join action, waiting-room text, error messages, and all invite-token
  status and error strings SHALL come from the `joinMeeting` translation
  namespace in `vi.json`
