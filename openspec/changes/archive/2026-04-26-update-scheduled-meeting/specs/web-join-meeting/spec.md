# MODIFIED Requirements

## Requirement: The web app handles approved, pending, denied, and failed join outcomes

The join-meeting flow SHALL map `requestJoin` responses into explicit
user-visible outcomes, SHALL preserve the validated join inputs across
non-terminal failures, and SHALL only hand off to the meeting-room page after
approval.

### Scenario: Approved join request navigates to meeting room

- **WHEN** `requestJoin` returns `status === 'APPROVED'` with `token` and
  `roomName`
- **THEN** the system SHALL store or pass the approved credentials in a
  web-accessible handoff channel
- **THEN** the handoff state SHALL also preserve the resolved backend meeting
  identifier for the approved room
- **THEN** the system SHALL navigate to `/workspace/meeting-room`

### Scenario: Pending join request enters waiting approval state

- **WHEN** `requestJoin` returns `status === 'PENDING'` with a `requestId`
- **THEN** the flow SHALL transition to a waiting-approval state
- **THEN** the system SHALL subscribe to join-request events for that
  `requestId`
- **THEN** the waiting UI SHALL remain visible until approval, denial, expiry,
  or terminal failure occurs

### Scenario: Denied join request shows mapped feedback

- **WHEN** `requestJoin` returns `status === 'DENIED'`
- **THEN** the system SHALL keep the user in the join flow instead of navigating
  away
- **THEN** invalid password outcomes SHALL be shown inline on the password field
- **THEN** guest-not-allowed, meeting-full, and meeting-not-live outcomes SHALL
  be shown through dialog, toast, or equivalent non-inline messaging
- **THEN** the most recent join-form values SHALL remain available for retry
  where retry is allowed

### Scenario: Transport failure preserves retry path

- **WHEN** a network or unexpected client error happens during lookup or join
  submission
- **THEN** the flow SHALL enter an error state
- **THEN** the user SHALL receive retryable feedback without losing the current
  join attempt inputs

# ADDED Requirements

## Requirement: Web meeting-room handoff preserves the active meeting identifier

The web app SHALL preserve the backend meeting identifier in the same tab-scoped
handoff state as the meeting-room token and room name so downstream room
features can target the active meeting resource.

### Scenario: Instant meeting launch stores the created meeting identifier

- **WHEN** the instant meeting flow reaches the ready-to-launch state after
  creation and start
- **THEN** the system SHALL save the created meeting identifier in
  session-scoped room handoff storage alongside the token and room name before
  navigating to the meeting room

### Scenario: Approved join handoff stores the resolved meeting identifier

- **WHEN** the join-meeting flow resolves a meeting and later receives approval
  for that join attempt
- **THEN** the system SHALL save the resolved meeting identifier in
  session-scoped room handoff storage alongside the approved token and room name
  before navigating to the meeting room

### Scenario: Meeting room consumes the stored meeting identifier

- **WHEN** the meeting-room page consumes handoff credentials for an approved or
  instant-launched room
- **THEN** it SHALL read the stored meeting identifier from the same
  session-scoped storage
- **THEN** the room state exposed to downstream components SHALL include that
  meeting identifier when present
