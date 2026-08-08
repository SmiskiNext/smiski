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

The app SHALL create instant meetings by calling the generated
`@smiskinext/smiski-ts` SDK operation (`createInstant`) whose transport is
bridged to Forge Remote, so that the request reaches the `meet` backend and
Atlassian attaches a signed Forge Invocation Token (FIT) as the
`Authorization: Bearer` credential on the outbound request. The transport SHALL
be one that also delivers the app system token to the gateway. The app SHALL NOT
attach `X-Tenant-ID`, `X-Account-Id`, or any other client-asserted
tenant/account identity header; deriving tenant and account identity from the
verified FIT is the gateway's responsibility and is out of scope for the app.
The request SHALL carry the numeric Jira issue and project context headers the
gateway needs to scope its permission check. The request body SHALL conform to
the OpenAPI `MeetCreateInstantMeetingRequest` contract, carrying a nested
`issueLink` (`issueId`, `issueKey`, `projectKey`), a `settings` object, a `host`
object, and the resolved `zoneId`. The instant-create flow SHALL NOT use the
in-memory mock; it SHALL depend on a reachable backend. On success the app SHALL
receive the created meeting snapshot and the host's LiveKit access details as an
SDK result whose `data` is present. On failure the app SHALL receive an SDK
result whose `error` is present and SHALL NOT throw a hand-written error type.

#### Scenario: Instant creation calls the backend via the SDK over Forge Remote

- **WHEN** the frontend creates an instant meeting with a valid payload
- **THEN** the app issues the create request through the SDK `createInstant`
  operation, whose transport is Forge Remote to the `meet` backend, and on a
  successful response obtains the created meeting snapshot together with the
  host's LiveKit token and room name in the result `data`

#### Scenario: Request body conforms to the instant contract

- **WHEN** the app builds the instant-create request from the form input
- **THEN** the body carries a nested `issueLink` object, a `settings` object, a
  `host` object, and a resolved `zoneId`, matching the OpenAPI
  `MeetCreateInstantMeetingRequest` schema

#### Scenario: Forge attaches the FIT and the app asserts no identity headers

- **WHEN** the app sends an instant-create request to the backend
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, and the app does not set `X-Tenant-ID` or `X-Account-Id`
  itself

#### Scenario: Instant creation from a modal surface carries context headers

- **WHEN** the instant-create form is opened as a platform modal and submitted
- **THEN** the request carries the numeric issue and project context headers, so
  the gateway resolves the caller's project permissions rather than returning an
  empty permission set

#### Scenario: Backend failure is surfaced as a result error, not mocked

- **WHEN** the backend rejects the instant-create request or is unreachable
- **THEN** the app surfaces the failure as an SDK result whose `error` is
  present, no meeting is created, and the flow does not fall back to the
  in-memory mock

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
the surface. For instant and scheduled creation, the create/schedule modals
SHALL read the SDK result `error` (the backend Problem Details body mapped to a
message) and SHALL show it as an actionable message in the form while keeping
the modal open for correction. For other flows still backed by thrown errors,
the app SHALL continue to present the error message.

The mapped error SHALL retain the machine-readable `code`, the `traceId`, and
the HTTP `status` from the backend response, so a user-reported failure remains
traceable to a single request.

#### Scenario: Backend validation error shown in the form

- **WHEN** the backend returns a validation problem for an instant-create or
  scheduled-create request
- **THEN** the modal reads the SDK result `error`, shows a message describing
  the failure, and the modal remains open for correction

#### Scenario: Error identifiers preserved through the transport

- **WHEN** the backend returns an error response carrying `code` and `traceId`
- **THEN** the mapped error exposes both values rather than a generic transport
  message

#### Scenario: Permission denial reported to the user

- **WHEN** the gateway or the backend denies a request because the caller lacks
  the required project permission
- **THEN** the surface presents the denial as an actionable message rather than
  rendering an empty or broken state

#### Scenario: Successful creation closes the modal

- **WHEN** the SDK result for an instant-create or scheduled-create request has
  `data` present and no `error`
- **THEN** the modal completes the flow (navigates or reports the created
  meeting id) and closes

### Requirement: Scheduled meeting creation through the backend

The app SHALL create scheduled meetings by calling the generated
`@smiskinext/smiski-ts` SDK operation (`schedule`) whose transport is bridged to
Forge Remote (`POST /api/1/meetings:schedule`), so that Atlassian attaches a
signed Forge Invocation Token (FIT) as the `Authorization: Bearer` credential on
the outbound request. The transport SHALL be one that also delivers the app
system token to the gateway. The app SHALL NOT attach `X-Tenant-ID`,
`X-Account-Id`, or any other client-asserted tenant/account identity header, and
the request body SHALL NOT contain a `host` object because host identity is
resolved from the request header by the backend. The request SHALL carry the
numeric Jira issue and project context headers the gateway needs to scope its
permission check. The request body SHALL conform to the OpenAPI
`MeetScheduleMeetingRequest` contract, carrying `organizerEmail`,
`organizerDisplayName`, `issueLink`, `settings`, `timeRange`, and the resolved
`zoneId`. The scheduled-create flow SHALL NOT use the in-memory mock; it SHALL
depend on a reachable backend. On success the app SHALL receive the created
meeting snapshot in `SCHEDULED` status as an SDK result whose `data` is present.
On failure the app SHALL receive an SDK result whose `error` is present and
SHALL NOT throw a hand-written error type.

#### Scenario: Scheduled creation calls the backend via the SDK over Forge Remote

- **WHEN** the host submits the schedule form with a valid payload
- **THEN** the app issues the create request through the SDK `schedule`
  operation, whose transport is Forge Remote to the `meet` backend at the
  schedule path, and on a successful response obtains the created meeting
  snapshot in `SCHEDULED` status in the result `data`

#### Scenario: Request body conforms to the scheduled contract without a host object

- **WHEN** the app builds the scheduled-create request from the form input
- **THEN** the body carries `organizerEmail`, `organizerDisplayName`,
  `issueLink`, `settings`, `timeRange`, and a resolved `zoneId`, and contains no
  `host` object, matching the OpenAPI `MeetScheduleMeetingRequest` schema

#### Scenario: Forge attaches the FIT and the app asserts no identity or host

- **WHEN** the app sends a scheduled-create request to the backend
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, the app does not set `X-Tenant-ID` or `X-Account-Id`, and
  the request body contains no `host` object

#### Scenario: Scheduled creation from a modal surface carries context headers

- **WHEN** the schedule form is opened as a platform modal and submitted
- **THEN** the request carries the numeric issue and project context headers, so
  the gateway resolves the caller's project permissions rather than returning an
  empty permission set

#### Scenario: Backend failure is surfaced as a result error, not mocked

- **WHEN** the backend rejects the scheduled-create request or is unreachable
- **THEN** the app surfaces the error-response failure as an SDK result whose
  `error` is present, no meeting is created, and the flow does not fall back to
  the in-memory mock

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

### Requirement: Status-aware meeting edit surface on the project page

The project page SHALL present a single `Edit meeting` modal that lets the host
manage a meeting's information, settings, and invitees, with sections gated by
the meeting status. The modal SHALL always show and allow editing of `title`,
`description`, and the linked Jira issue. The modal SHALL show the start
date/time and time zone controls but SHALL disable them when the meeting status
is not `SCHEDULED`. The modal SHALL show the settings and invitee-management
sections when the meeting status is `SCHEDULED` or `RUNNING`, and SHALL hide
those sections when the status is `COMPLETED` or `CANCELED`. On save, the app
SHALL send only the operations whose values changed, using the existing update,
settings-replacement, and invitee add/remove backend operations.

#### Scenario: Editing a scheduled meeting exposes every section

- **WHEN** the host opens the edit modal for a `SCHEDULED` meeting
- **THEN** the modal shows editable title, description, issue link, start
  date/time, time zone, settings, and invitee management

#### Scenario: Editing a running meeting locks time fields but keeps settings and invitees

- **WHEN** the host opens the edit modal for a `RUNNING` meeting
- **THEN** the modal shows editable title, description, issue link, settings,
  and invitee management, and the start date/time and time zone controls are
  disabled

#### Scenario: Editing a completed or canceled meeting exposes only information

- **WHEN** the host opens the edit modal for a `COMPLETED` or `CANCELED` meeting
- **THEN** the modal shows editable title, description, and issue link, the time
  fields are disabled, and the settings and invitee sections are hidden

#### Scenario: Save sends only changed operations

- **WHEN** the host saves the edit modal after changing only the title
- **THEN** the app issues the meeting information update and does not call the
  settings-replacement or invitee endpoints

### Requirement: Editable issue link in the meeting edit surface

The meeting edit modal SHALL let the host change the Jira issue the meeting is
linked to, choosing from issues in the current project sourced from Jira. When
the host selects a different issue, the app SHALL send the newly selected
`issueId`, `issueKey`, and `projectKey` on the meeting information update. When
the host does not change the issue, the app SHALL preserve the meeting's current
issue link.

#### Scenario: Host changes the linked issue

- **WHEN** the host selects a different project issue in the edit modal and
  saves
- **THEN** the meeting information update carries the newly selected `issueId`,
  `issueKey`, and `projectKey`

#### Scenario: Unchanged issue link is preserved

- **WHEN** the host saves the edit modal without changing the linked issue
- **THEN** the meeting information update carries the meeting's existing issue
  link

### Requirement: Invitee management is add and remove only

The meeting edit surface SHALL let the host add new invitees and remove existing
invitees, and SHALL NOT provide any control to edit an existing invitee's
identity (`accountId`, `email`, or `displayName`). Adding and removing invitees
SHALL be available only when the meeting status is `SCHEDULED` or `RUNNING`.

#### Scenario: Host adds and removes invitees on an editable meeting

- **WHEN** the host adds one workspace user and removes one existing invitee on
  a `SCHEDULED` or `RUNNING` meeting and saves
- **THEN** the app calls the add-invitees operation for the added user and the
  remove-invitees operation for the removed invitee

#### Scenario: No control edits an existing invitee's identity

- **WHEN** the host views the invitee list in the edit surface
- **THEN** the surface offers add and remove controls only and exposes no field
  to change an existing invitee's account, email, or display name

### Requirement: Meeting action policy exposes edit across statuses without a separate settings action

The frontend meeting-action policy SHALL expose the `EDIT` action to the host in
every meeting status (`SCHEDULED`, `RUNNING`, `COMPLETED`, `CANCELED`), so that
settings and invitee management are reached through the unified edit surface on
the project page. The policy SHALL NOT expose a separate `SETTINGS` action on
the project page. The policy SHALL continue to expose `START`/`CANCEL` for
`SCHEDULED` and `JOIN`/`END` for `RUNNING` to the host as before, and SHALL
expose no host management actions to a non-host user.

#### Scenario: Host sees edit on a completed meeting

- **WHEN** the action policy is evaluated for a host on a `COMPLETED` meeting
- **THEN** the available actions include `EDIT` and the view actions, and do not
  include a separate `SETTINGS` action

#### Scenario: Settings reached through edit on a running meeting

- **WHEN** the action policy is evaluated for a host on a `RUNNING` meeting
- **THEN** the available actions include `EDIT` and `JOIN` and `END`, and do not
  include a separate `SETTINGS` action

#### Scenario: Non-host sees no management actions

- **WHEN** the action policy is evaluated for a non-host user on any status
- **THEN** the available actions include no `EDIT`, `SETTINGS`, `CANCEL`, or
  `END` action

### Requirement: Issue panel is limited to create, list, view, and join

The issue panel surface SHALL be limited to creating meetings (instant and
scheduled), listing the issue's meetings, viewing meeting detail and history,
and joining a `RUNNING` meeting. The issue panel SHALL NOT offer edit, cancel,
start, end, or settings actions.

#### Scenario: Issue panel offers view and join only for a running meeting

- **WHEN** the host views a `RUNNING` meeting in the issue panel
- **THEN** the available actions are limited to join and view detail/history,
  and no edit, cancel, end, or settings action is shown

#### Scenario: Issue panel offers view only for a scheduled meeting

- **WHEN** the host views a `SCHEDULED` meeting in the issue panel
- **THEN** the available actions are limited to view detail/history, and no
  edit, cancel, start, or settings action is shown

#### Scenario: Issue panel keeps meeting creation

- **WHEN** the host with edit permission views the issue panel
- **THEN** the panel offers the instant-meeting and schedule-meeting create
  actions
