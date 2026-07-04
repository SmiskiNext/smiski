# MODIFIED Requirements

## Requirement: The web app resolves meeting requirements before requesting entry

The join-meeting flow SHALL resolve the meeting by short code before sending a
join request, SHALL preserve the user's current form inputs across retryable
failures, and SHALL branch on whether the resolved meeting requires a password.

### Scenario: Meeting lookup succeeds without password requirement

- **WHEN** the user submits a valid short code and
  `getMeetingByShortCode({ query: { code } })` returns a meeting whose
  `settings.requirePassword` is `false`
- **THEN** the system SHALL store the resolved meeting identifier for the
  current join attempt
- **THEN** the flow SHALL proceed directly to join-request submission without
  asking for a password

### Scenario: Meeting lookup requires password before request submission

- **WHEN** the short-code lookup succeeds and `settings.requirePassword` is
  `true` and the user has not yet provided a password
- **THEN** the system SHALL transition into a password-required state
- **THEN** the password field SHALL be shown and required before `requestJoin`
  is called

### Scenario: Meeting lookup failure blocks the join request

- **WHEN** `getMeetingByShortCode({ query: { code } })` returns not found or
  fails before a meeting is resolved
- **THEN** the system SHALL NOT call `requestJoin`
- **THEN** the meeting code field SHALL show inline validation or retryable
  error feedback appropriate to the failure type
- **THEN** previously entered join-form values SHALL remain available for
  correction and resubmission

## Requirement: The web app submits join requests with mode-aware inputs and device identity

The join-meeting flow SHALL submit
`requestJoin({ path: { id }, body: { displayName, deviceId, password? } })`
using a tab-scoped device identifier, mode-appropriate display-name behavior,
and schema-validated form inputs.

### Scenario: Guest join request includes user-entered display name

- **WHEN** a guest submits a join request after meeting lookup succeeds
- **THEN** the request body SHALL include the guest-entered `displayName`
- **THEN** the request body SHALL include a `deviceId` generated with
  `crypto.randomUUID()` and reused from `sessionStorage` for the current tab
- **THEN** the request body SHALL include `password` when the resolved meeting
  requires one

### Scenario: Authenticated join request uses the current user display name

- **WHEN** an authenticated user submits a join request after meeting lookup
  succeeds
- **THEN** the request body SHALL include the authenticated user's display name
- **THEN** the request body SHALL include the tab-scoped `deviceId`
- **THEN** the request body SHALL include `password` only when the resolved
  meeting requires one

### Scenario: Join form validation blocks incomplete submissions

- **WHEN** the user submits the join form without the required meeting code,
  required guest display name, or required password in the password step
- **THEN** the system SHALL prevent submission of the current step
- **THEN** the relevant field SHALL show inline validation feedback

### Scenario: Invalid password denial is shown inline

- **WHEN** `requestJoin` returns a denial that represents an invalid password
- **THEN** the flow SHALL transition into a denied or retryable state without
  leaving the page
- **THEN** the password field SHALL show inline invalid-password feedback and
  allow resubmission

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
