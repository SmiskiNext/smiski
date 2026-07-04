# ADDED Requirements

## Requirement: Authenticated and guest users can enter the web pre-join flow

The web app SHALL provide two entry points into the join-meeting experience: an
authenticated workspace flow at `/workspace/green-room` and a public guest flow
at `/{locale}/join/{code}`. Both entry points SHALL use the same join-meeting
feature logic while applying mode-specific routing and input rules.

### Scenario: Authenticated user opens the green-room flow

- **WHEN** an authenticated user navigates to `/workspace/green-room`
- **THEN** the system SHALL render the shared join-meeting flow in authenticated
  mode
- **THEN** the meeting code SHALL be accepted from query params or manual entry
- **THEN** the display name input SHALL be prefilled from the authenticated user
  context instead of being required as guest input

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
approval. An approved response from the backend MAY reflect an implicit
lifecycle elevation when the requester is the meeting host and the meeting was
previously `SCHEDULED`; the client SHALL treat this case identically to any
other approved response.

### Scenario: Approved join request navigates to meeting room

- **WHEN** `requestJoin` returns `status === 'APPROVED'` with `token` and
  `roomName`
- **THEN** the system SHALL store or pass the approved credentials in a
  web-accessible handoff channel
- **THEN** the handoff state SHALL also preserve the resolved backend meeting
  identifier for the approved room
- **THEN** the system SHALL navigate to `/workspace/meeting-room`

### Scenario: Host approval after implicit elevation behaves like any approval

- **WHEN** an authenticated host requests join on a meeting whose previously
  observed status was `SCHEDULED` and the backend returns
  `status === 'APPROVED'` with `token` and `roomName`
- **THEN** the join flow SHALL proceed exactly as for any other approved
  response without special-casing the prior status

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

## Requirement: Pending join requests react to waiting-room server-sent events

For pending approvals, the web app SHALL subscribe to
`GET /api/v1/joinRequests/{requestId}/events`, interpret terminal event types,
and retry failed connections at most three times with exponential backoff.

### Scenario: Approval event completes the join flow

- **WHEN** the event stream receives `join_request_approved` with
  `{token, roomName}`
- **THEN** the system SHALL stop the active event subscription
- **THEN** the system SHALL store or pass the approved credentials
- **THEN** the system SHALL navigate to `/workspace/meeting-room`

### Scenario: Denial event returns the user to actionable feedback

- **WHEN** the event stream receives `join_request_denied` with a denial
  `reason`
- **THEN** the system SHALL stop the active event subscription
- **THEN** the flow SHALL leave the waiting state
- **THEN** the user SHALL see denial feedback mapped from the returned reason

### Scenario: Expiration event invalidates the pending request

- **WHEN** the event stream receives `join_request_expired`
- **THEN** the system SHALL stop the active event subscription
- **THEN** the flow SHALL enter an expired state
- **THEN** the user SHALL be told that the request expired and must start a new
  join attempt

### Scenario: Transient SSE failure retries with bounded exponential backoff

- **WHEN** the event stream disconnects before a terminal event is received and
  the retry count is below 3
- **THEN** the system SHALL attempt to reconnect after delays of 1 second, 2
  seconds, and 4 seconds for successive failures
- **THEN** the waiting UI SHALL remain active during retry attempts

### Scenario: Repeated SSE failure surfaces terminal error

- **WHEN** the event stream fails again after the third retry attempt without
  receiving a terminal event
- **THEN** the system SHALL stop retrying
- **THEN** the flow SHALL enter an error state with retryable user feedback

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
