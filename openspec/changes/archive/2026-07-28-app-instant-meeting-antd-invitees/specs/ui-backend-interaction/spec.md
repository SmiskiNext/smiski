## ADDED Requirements

### Requirement: Workspace-user search through the resolver

The app SHALL search Jira site (workspace) users through a Forge resolver
function rather than from the browser. The resolver SHALL query Jira as the
invoking user so the user's own permissions govern the result. When no search
term is provided, the resolver SHALL return an initial list of users; when a
term is provided, the resolver SHALL return users matching that term. The
resolver SHALL return only active, human Atlassian accounts, and SHALL map each
result to `accountId`, `displayName`, `email`, and `avatarUrl`. The tenant and
account identity SHALL be derived from the Forge invocation context and SHALL
NOT be supplied by the browser.

#### Scenario: Empty query returns an initial user list

- **WHEN** the frontend requests workspace users with no search term
- **THEN** the resolver returns an initial list of active workspace users mapped
  to `accountId`, `displayName`, `email`, and `avatarUrl`

#### Scenario: Query returns matching users

- **WHEN** the frontend requests workspace users with a search term
- **THEN** the resolver returns workspace users matching the term, mapped to the
  same fields

#### Scenario: Inactive and non-human accounts are excluded

- **WHEN** the underlying Jira result includes inactive users or non-human
  (app/customer) accounts
- **THEN** those entries are excluded from the returned list

#### Scenario: Insufficient permission surfaces as an error

- **WHEN** the invoking user is not permitted to browse users and Jira rejects
  the search
- **THEN** the resolver reports the failure to the caller instead of returning a
  user list, and the failure does not create any meeting

### Requirement: Instant meeting creation through the backend SDK

The app SHALL create instant meetings by invoking a Forge resolver that calls
the Smiski backend using the backend SDK's create-instant operation. The
resolver SHALL forward the request to the backend gateway attaching the tenant
identifier (from the Forge `cloudId`) and the account identifier (from the Forge
`accountId`) as request headers. The instant-create flow SHALL NOT use the
in-memory mock; it SHALL depend on a reachable backend. On success the resolver
SHALL return the created meeting snapshot and the host's LiveKit access details
to the caller.

#### Scenario: Successful creation returns snapshot and LiveKit details

- **WHEN** the frontend invokes instant-meeting creation with a valid payload
  and the backend responds successfully
- **THEN** the resolver returns the created meeting snapshot together with the
  host's LiveKit token and room name

#### Scenario: Identity headers are attached server-side

- **WHEN** the resolver forwards an instant-create request to the backend
- **THEN** the request carries the tenant identifier and account identifier
  taken from the Forge invocation context, not from the browser

#### Scenario: Backend failure is surfaced, not mocked

- **WHEN** the backend rejects the instant-create request or is unreachable
- **THEN** the resolver surfaces the error to the caller and no meeting is
  created, and the flow does not fall back to the in-memory mock

### Requirement: Invitees carry frontend-resolved identity

When the host selects invitees in the instant-meeting form, the app SHALL send
each invitee to the backend with its `email`, `accountId`, and `displayName`.
When the host selects no invitees, the app SHALL create the meeting with no
invitees.

#### Scenario: Selected invitees are sent with full identity

- **WHEN** the host submits an instant meeting with one or more selected
  workspace users
- **THEN** each invitee is sent to the backend with its `email`, `accountId`,
  and `displayName`

#### Scenario: No invitees still creates the meeting

- **WHEN** the host submits an instant meeting without selecting any invitee
- **THEN** the meeting is created with an empty invitee list

### Requirement: Frontend error handling for resolver failures

The app SHALL present resolver and backend failures to the user without crashing
the surface. Validation and problem responses returned by the backend SHALL be
shown as an actionable message in the form.

#### Scenario: Backend validation error shown in the form

- **WHEN** the backend returns a validation problem for an instant-create
  request
- **THEN** the form shows an error message describing the failure and the modal
  remains open for correction
