# get-meeting-detail Specification

## ADDED Requirements

### Requirement: Get meeting detail endpoint

The system SHALL expose `GET /api/1/meetings/{id}` that returns a single meeting
belonging to the caller's tenant together with its invitee list and its joined
participant list in one response body. The endpoint SHALL use the HTTP `GET`
method, resolve the tenant from the `X-Tenant-ID` header, and require an
`X-Account-Id` header. Any authenticated account within the meeting's tenant
SHALL be permitted to read the meeting; access SHALL NOT be restricted to the
host. The successful response SHALL be `200 OK` with the representation returned
directly without a wrapping envelope, and SHALL NOT include the tenant
identifier.

#### Scenario: Existing meeting is returned with its people

- **WHEN** a client sends `GET /api/1/meetings/{id}` with a valid `X-Tenant-ID`
  and `X-Account-Id` for a meeting that exists in that tenant
- **THEN** the response is `200 OK` containing the meeting snapshot, an
  `invitees` array, and a `participants` array

#### Scenario: A non-host tenant member may read the meeting

- **WHEN** an authenticated account that is not the meeting host requests
  `GET /api/1/meetings/{id}` for a meeting in its own tenant
- **THEN** the response is `200 OK` with the meeting detail, and the request is
  not rejected for lack of host ownership

#### Scenario: Missing account header is rejected

- **WHEN** a client sends `GET /api/1/meetings/{id}` without an `X-Account-Id`
  header
- **THEN** the request fails with a `400` Problem Details response and no
  meeting detail is returned

### Requirement: Tenant isolation and not-found semantics

The system SHALL return `404` with a Problem Details body and the code
`MEETING_NOT_FOUND` when the requested meeting does not exist, has been
soft-deleted, or belongs to a different tenant than the request. Soft-deleted
and non-existent meetings SHALL be indistinguishable to the caller.

#### Scenario: Unknown meeting id returns 404

- **WHEN** a client requests a meeting id that does not exist in the tenant
- **THEN** the response is `404` Problem Details with code `MEETING_NOT_FOUND`

#### Scenario: Soft-deleted meeting returns 404

- **WHEN** a client requests a meeting that has been soft-deleted
- **THEN** the response is `404` Problem Details with code `MEETING_NOT_FOUND`
  and is indistinguishable from a request for a non-existent meeting

#### Scenario: Meeting from another tenant is not visible

- **WHEN** a meeting exists under one tenant and a client requests it with a
  different `X-Tenant-ID`
- **THEN** the response is `404` Problem Details with code `MEETING_NOT_FOUND`
  and no meeting detail is leaked across tenants

### Requirement: Meeting detail shape

The meeting portion of the response SHALL contain the meeting's full snapshot
fields — `id`, `hostId`, `shortCode`, `type`, `status`, `title`, `description`,
`issueLink`, `settings`, `startTime` (nullable), `endTime` (nullable), `zoneId`,
`organizerEmail`, `organizerDisplayName`, `calendarUid`, `calendarSequence`, and
`createdAt` — and SHALL NOT include the tenant identifier.

#### Scenario: Meeting snapshot exposes detail fields without tenant id

- **WHEN** a client successfully retrieves a meeting
- **THEN** the `meeting` object includes `id`, `hostId`, `shortCode`, `type`,
  `status`, `title`, `settings`, and `createdAt`, and does not include the
  tenant identifier

### Requirement: Invitee list in meeting detail

The response SHALL include an `invitees` array listing the meeting's active
(non-removed) invitees. Each entry SHALL carry `id` (the invitee identity used
to remove the invitee via `POST /api/1/meetings/{id}/invitees:batchDelete`),
`accountId`, `email`, `displayName`, `status` (the RSVP status), `invitedAt`,
and `respondedAt` (nullable when the invitee has not yet responded).

#### Scenario: Invitees are listed with their RSVP status

- **WHEN** a meeting has invitees and a client retrieves it
- **THEN** the `invitees` array contains one entry per active invitee, each with
  its `id`, `accountId`, `email`, `displayName`, `status`, `invitedAt`, and
  `respondedAt`

#### Scenario: Invitee entry exposes its removal id

- **WHEN** a client retrieves a meeting that has active invitees
- **THEN** each invitee entry includes a non-empty `id` that can be submitted to
  `POST /api/1/meetings/{id}/invitees:batchDelete`

#### Scenario: Meeting with no invitees returns an empty invitee list

- **WHEN** a meeting has no active invitees and a client retrieves it
- **THEN** the `invitees` array is empty and the request still succeeds with
  `200 OK`

### Requirement: Participant list in meeting detail

The response SHALL include a `participants` array derived from the meeting's
participation logs, collapsed to exactly one entry per account regardless of how
many times that account joined. The list SHALL include accounts that have
already left. Each entry SHALL carry `accountId`, `displayName`, `role`,
`joinedAt`, and `leftAt`. For a given account, `joinedAt` SHALL be the earliest
join time across that account's sessions, and `leftAt` SHALL be `null` when the
account has any still-open session and otherwise the latest leave time across
its sessions.

#### Scenario: A rejoining account appears once

- **WHEN** an account joined the meeting more than once (producing multiple
  participation-log rows) and a client retrieves the meeting
- **THEN** the `participants` array contains exactly one entry for that account
  with `joinedAt` equal to its earliest join time

#### Scenario: A participant who left is still listed

- **WHEN** an account joined and then left the meeting and a client retrieves it
- **THEN** the `participants` array includes that account with a non-null
  `leftAt`

#### Scenario: A still-present participant has a null leftAt

- **WHEN** an account has an open session (has not left) and a client retrieves
  the meeting
- **THEN** that account's entry has `leftAt` equal to `null`

#### Scenario: Meeting nobody joined returns an empty participant list

- **WHEN** a meeting has no participation logs and a client retrieves it
- **THEN** the `participants` array is empty and the request still succeeds with
  `200 OK`
