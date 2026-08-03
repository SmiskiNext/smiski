## ADDED Requirements

### Requirement: Project filter

The system SHALL filter the listing by the optional `projectKey` field. When
`projectKey` is null, omitted, or blank, the system SHALL return meetings from
every project in the tenant. When `projectKey` is present and non-blank, the
system SHALL return only meetings whose linked Jira project key equals
`projectKey` using an exact, case-sensitive match. The `projectKey` filter SHALL
combine with all other filters using logical AND.

#### Scenario: Project filter restricts to one project key

- **WHEN** a client lists with `projectKey` = `SMISKI`
- **THEN** every returned meeting is linked to a Jira issue whose project key is
  `SMISKI`, and meetings from other projects are excluded

#### Scenario: No project filter returns all projects

- **WHEN** a client lists with `projectKey` omitted or blank
- **THEN** meetings linked to issues in different projects may all appear

#### Scenario: Project and issue filters combine with AND

- **WHEN** a client lists with `projectKey` = `SMISKI` and `issueKey` =
  `OTHER-1`, where `OTHER-1` belongs to a different project
- **THEN** the response contains no meetings, because no meeting satisfies both
  the project and issue constraints

## MODIFIED Requirements

### Requirement: List meetings search endpoint

The system SHALL expose `POST /api/1/meetings` that returns meetings belonging
to the caller's tenant. The endpoint SHALL use the HTTP `POST` method (not `GET`
or `QUERY`) and accept a JSON request body in which every field is optional:
`creatorId` (string), `search` (string), `statuses` (array of
`SCHEDULED|RUNNING|COMPLETED|CANCELED`), `issueKey` (string), `projectKey`
(string), `sort` (`CREATED_AT` or `START_TIME`), `pageSize` (integer), and
`pageToken` (opaque string). The endpoint SHALL require the `view-meeting`
project permission — if the caller's permission context does not contain
`view-meeting`, the endpoint SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` before executing the
use case. When the body is absent or empty, the endpoint SHALL behave as if all
fields were omitted. The tenant SHALL be resolved from the `X-Tenant-ID` header,
and the request SHALL require an `X-Account-Id` header.

#### Scenario: Empty body returns tenant meetings with defaults

- **WHEN** a client sends `POST /api/1/meetings` with a valid `X-Tenant-ID`, an
  `X-Account-Id` header, an empty JSON body `{}`, and the caller has
  `view-meeting` permission
- **THEN** the response is `200 OK` containing meetings from that tenant ordered
  by created-at descending with default page size

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends `POST /api/1/meetings`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting list is returned

### Requirement: Meeting summary shape

Each item in `data` SHALL be a meeting summary containing `id`, `hostId`,
`shortCode`, `title`, `description`, `issueId`, `issueKey`, `projectKey`,
`type`, `status`, `startTime` (null for meetings without a scheduled start),
`endTime` (nullable), `createdAt`, and `settings`. The `issueId`, `issueKey`,
and `projectKey` fields SHALL always be present and non-null. The summary SHALL
NOT include the tenant identifier.

#### Scenario: Summary exposes list-relevant fields

- **WHEN** a client lists meetings
- **THEN** each returned item includes `id`, `hostId`, `shortCode`, `title`,
  `issueId`, `issueKey`, `projectKey`, `type`, `status`, `startTime`, `endTime`,
  `createdAt`, and `settings`, and does not include the tenant identifier

#### Scenario: Summary carries the linked issue and project identifiers

- **WHEN** a client lists a meeting linked to issue `SMISKI-102` (issue id
  `10102`) in project `SMISKI`
- **THEN** that item's `issueId` is `10102`, `issueKey` is `SMISKI-102`, and
  `projectKey` is `SMISKI`
