## MODIFIED Requirements

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
