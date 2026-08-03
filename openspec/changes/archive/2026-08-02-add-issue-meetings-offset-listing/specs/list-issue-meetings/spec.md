## ADDED Requirements

### Requirement: Issue-scoped meeting listing endpoint

The system SHALL expose `POST /api/1/issues/{issueId}/meetings` that returns the
meetings linked to the Jira issue identified by the `{issueId}` path variable
within the caller's tenant. On this endpoint the `POST` method denotes a
list/search operation and SHALL NOT create a meeting. The endpoint SHALL require
the `view-meeting` project permission — if the caller's permission context does
not contain `view-meeting`, the endpoint SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` before executing the
use case. The tenant SHALL be resolved from the `X-Tenant-ID` header, and the
request SHALL require an `X-Account-Id` header used only for authentication
context, never as a listing filter.

The `{issueId}` SHALL be matched exactly against the meeting's stored linked
Jira issue id (the numeric `issueId`, not the human-readable issue key).

#### Scenario: Issue with linked meetings returns them

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with a valid
  `X-Tenant-ID`, an `X-Account-Id` header, the caller has `view-meeting`
  permission, and three non-deleted meetings in the tenant are linked to issue
  id `10102`
- **THEN** the response is `200 OK` and `data` contains exactly those three
  meetings

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends
  `POST /api/1/issues/10102/meetings`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting list is returned

#### Scenario: Missing account header

- **WHEN** the caller sends `POST /api/1/issues/10102/meetings` without the
  `X-Account-Id` header
- **THEN** the response is `400 application/problem+json` with code
  `VALIDATION_ERROR`

#### Scenario: Issue id filter is exact

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` and the tenant
  also has meetings linked to issue id `10103`
- **THEN** every returned meeting is linked to issue id `10102` and no meeting
  linked to `10103` appears

### Requirement: Offset pagination with total count

The endpoint SHALL paginate results using offset pagination. The request body
SHALL accept optional `offset` (default `0`, minimum `0`) and `pageSize`
(default `20`, minimum `1`, maximum `50`); an absent or empty body SHALL behave
as if both were omitted. The response body SHALL contain an array `data` of
meeting summaries and a `meta` object with `total` (the count of all non-deleted
meetings linked to the issue in the tenant across every page), `offset` (the
zero-based start index used for this page), `pageSize` (the maximum items per
page requested), and `hasNext` (whether more results exist after this page).
`hasNext` SHALL be true if and only if `offset` plus the number of returned
items is less than `total`.

#### Scenario: First page reports total and more pages

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with body
  `{"offset":0,"pageSize":2}` and the issue has five linked meetings
- **THEN** the response is `200 OK`, `data` contains two items, `meta.total` is
  `5`, `meta.offset` is `0`, `meta.pageSize` is `2`, and `meta.hasNext` is true

#### Scenario: Second page returns the following items

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with body
  `{"offset":2,"pageSize":2}` and the issue has five linked meetings
- **THEN** the response is `200 OK`, `data` contains the two items at index 2
  and 3, `meta.offset` is `2`, and `meta.hasNext` is true

#### Scenario: Last page clears hasNext

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with body
  `{"offset":4,"pageSize":2}` and the issue has five linked meetings
- **THEN** the response is `200 OK`, `data` contains one item, `meta.total` is
  `5`, and `meta.hasNext` is false

#### Scenario: Empty body uses defaults

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with an empty body
  `{}`
- **THEN** the response is `200 OK` with `meta.offset` `0` and `meta.pageSize`
  `20`

#### Scenario: Issue with no meetings returns an empty page

- **WHEN** a client sends `POST /api/1/issues/99999/meetings` for an issue id
  that has no linked meetings in the tenant
- **THEN** the response is `200 OK`, `data` is an empty array, `meta.total` is
  `0`, and `meta.hasNext` is false

#### Scenario: Offset beyond the total returns an empty page

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with body
  `{"offset":10,"pageSize":2}` and the issue has five linked meetings
- **THEN** the response is `200 OK`, `data` is an empty array, `meta.total` is
  `5`, and `meta.hasNext` is false

#### Scenario: Page size over the maximum is rejected

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with body
  `{"pageSize":51}`
- **THEN** the response is `400 application/problem+json` with code
  `VALIDATION_ERROR`

#### Scenario: Negative offset is rejected

- **WHEN** a client sends `POST /api/1/issues/10102/meetings` with body
  `{"offset":-1}`
- **THEN** the response is `400 application/problem+json` with code
  `VALIDATION_ERROR`

### Requirement: Ordering and soft-delete exclusion

The system SHALL order results newest-first by creation time descending, with a
total-order tie-breaker on the meeting id descending, so no meeting is skipped
or duplicated across pages. The system SHALL always exclude soft-deleted
meetings (`deleted_at` is set) from both the returned `data` and the
`meta.total` count.

#### Scenario: Results ordered newest-first

- **WHEN** a client lists meetings for an issue linked to several meetings
  created at different times
- **THEN** the meetings appear from most recently created to least recently
  created

#### Scenario: Ties broken deterministically across pages

- **WHEN** two meetings linked to the issue share the same creation timestamp
  and a client pages through the results
- **THEN** the two meetings are ordered by descending id and neither is skipped
  nor duplicated between pages

#### Scenario: Soft-deleted meeting excluded from data and total

- **WHEN** one of the meetings linked to an issue has been soft-deleted and a
  client lists that issue's meetings
- **THEN** the soft-deleted meeting does not appear in `data` and is not counted
  in `meta.total`

### Requirement: Meeting summary shape

Each item in `data` SHALL be a meeting summary containing `id`, `hostId`,
`shortCode`, `title`, `description`, `issueId`, `issueKey`, `projectKey`,
`type`, `status`, `startTime` (null for meetings without a scheduled start),
`endTime` (nullable), `createdAt`, and `settings`. The `issueId`, `issueKey`,
and `projectKey` fields SHALL always be present and non-null. The summary SHALL
NOT include the tenant identifier.

#### Scenario: Summary exposes list-relevant fields

- **WHEN** a client lists an issue's meetings
- **THEN** each returned item includes `id`, `hostId`, `shortCode`, `title`,
  `issueId`, `issueKey`, `projectKey`, `type`, `status`, `startTime`, `endTime`,
  `createdAt`, and `settings`, and does not include the tenant identifier

#### Scenario: Summary carries the linked issue and project identifiers

- **WHEN** a client lists meetings for issue id `10102`, whose issue key is
  `SMISKI-102` in project `SMISKI`
- **THEN** each item's `issueId` is `10102`, `issueKey` is `SMISKI-102`, and
  `projectKey` is `SMISKI`
