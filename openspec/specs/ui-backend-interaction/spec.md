# ui-backend-interaction Specification

## Purpose

TBD - created by archiving change app-instant-meeting-antd-invitees. Update
Purpose after archive.

## Requirements

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

The app SHALL create instant meetings by calling the `meet` backend through
Forge Remote from the Custom UI, so that Atlassian attaches a signed Forge
Invocation Token (FIT) as the `Authorization: Bearer` credential on the outbound
request. The app SHALL NOT attach `X-Tenant-ID`, `X-Account-Id`, or any other
client-asserted tenant/account identity header; deriving tenant and account
identity from the verified FIT is the gateway's responsibility and is out of
scope for the app. The instant-create flow SHALL NOT use the in-memory mock; it
SHALL depend on a reachable backend. On success the app SHALL receive the
created meeting snapshot and the host's LiveKit access details.

#### Scenario: Instant creation calls the backend via Forge Remote

- **WHEN** the frontend creates an instant meeting with a valid payload
- **THEN** the app issues the create request through Forge Remote to the `meet`
  backend and, on a successful response, obtains the created meeting snapshot
  together with the host's LiveKit token and room name

#### Scenario: Forge attaches the FIT and the app asserts no identity headers

- **WHEN** the app sends an instant-create request to the backend
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, and the app does not set `X-Tenant-ID` or `X-Account-Id`
  itself

#### Scenario: Backend failure is surfaced, not mocked

- **WHEN** the backend rejects the instant-create request or is unreachable
- **THEN** the app surfaces the error to the caller and no meeting is created,
  and the flow does not fall back to the in-memory mock

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

### Requirement: Scheduled meeting creation through the backend

The app SHALL create scheduled meetings by calling the `meet` backend through
Forge Remote from the Custom UI (`POST /api/1/meetings:schedule`), so that
Atlassian attaches a signed Forge Invocation Token (FIT) as the
`Authorization: Bearer` credential on the outbound request. The app SHALL NOT
attach `X-Tenant-ID`, `X-Account-Id`, or any other client-asserted
tenant/account identity header, and the request body SHALL NOT contain a `host`
object because host identity is resolved from the request header by the backend.
The scheduled-create flow SHALL NOT use the in-memory mock; it SHALL depend on a
reachable backend. On success the app SHALL receive the created meeting snapshot
in `SCHEDULED` status.

#### Scenario: Scheduled creation calls the backend via Forge Remote

- **WHEN** the host submits the schedule form with a valid payload
- **THEN** the app issues the create request through Forge Remote to the `meet`
  backend at the schedule path and, on a successful response, obtains the
  created meeting snapshot in `SCHEDULED` status

#### Scenario: Forge attaches the FIT and the app asserts no identity or host

- **WHEN** the app sends a scheduled-create request to the backend
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, the app does not set `X-Tenant-ID` or `X-Account-Id`, and
  the request body contains no `host` object

#### Scenario: Backend failure is surfaced, not mocked

- **WHEN** the backend rejects the scheduled-create request or is unreachable
- **THEN** the app surfaces the problem+json error to the caller, no meeting is
  created, and the flow does not fall back to the in-memory mock

### Requirement: Scheduled invitees carry frontend-resolved identity

When the host selects invitees in the schedule-meeting form, the app SHALL send
each invitee to the backend with its `email`, `accountId`, and `displayName`.
When the host selects no invitees, the app SHALL create the meeting with an
empty invitee list.

#### Scenario: Selected invitees are sent with full identity

- **WHEN** the host submits a scheduled meeting with one or more selected
  workspace users
- **THEN** each invitee is sent to the backend with its `email`, `accountId`,
  and `displayName`

#### Scenario: No invitees still creates the meeting

- **WHEN** the host submits a scheduled meeting without selecting any invitee
- **THEN** the meeting is created with an empty invitee list

### Requirement: Meeting time zone resolved from the user profile

For both instant and scheduled meeting creation, the app SHALL resolve the
meeting `zoneId` from the invoking user's Jira profile time zone (read from
`/myself`). When the profile time zone is present and a resolvable IANA zone id,
the app SHALL use it; when it is absent or invalid, the app SHALL fall back to
the browser's local time zone. For scheduled meetings the resolved zone SHALL be
the default selection of the form's time-zone control, which the host MAY change
before submitting.

#### Scenario: Profile time zone is used as the meeting zone

- **WHEN** the invoking user's Jira profile provides a valid IANA time zone and
  the host creates a meeting
- **THEN** the create request carries that profile time zone as the `zoneId`

#### Scenario: Missing profile time zone falls back to the browser zone

- **WHEN** the invoking user's Jira profile omits a time zone or provides an
  unresolvable value
- **THEN** the app uses the browser's local time zone as the `zoneId`
