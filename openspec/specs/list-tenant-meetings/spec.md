# list-tenant-meetings Specification

## Purpose

Provide a tenant-scoped meeting search and listing capability via
`POST /api/1/meetings`. The endpoint returns meetings belonging to the caller's
tenant with optional filtering (creator, status, issue key, free-text search),
deterministic ordering across two sort modes, and keyset (cursor) pagination
using an opaque, sort-bound signed token. Soft-deleted meetings are always
excluded and the tenant identifier is never exposed in responses.

## Requirements

### Requirement: List meetings search endpoint

The system SHALL expose `POST /api/1/meetings` that returns meetings belonging
to the caller's tenant. The endpoint SHALL use the HTTP `POST` method (not `GET`
or `QUERY`) and accept a JSON request body in which every field is optional:
`creatorId` (string), `search` (string), `statuses` (array of
`SCHEDULED|RUNNING|COMPLETED|CANCELED`), `issueKey` (string), `projectKey`
(string), `sort` (`CREATED_AT` or `START_TIME`), `pageSize` (integer), and
`pageToken` (opaque string). The endpoint SHALL require the `view-meeting`
project permission — if the caller's permission context does not contain
`view-meeting`, the endpoint SHALL reject the request with a `403` Problem
Details response and code `NOT_AUTHORIZED` before executing the use case. When
the body is absent or empty, the endpoint SHALL behave as if all fields were
omitted. The tenant SHALL be resolved from the `X-Tenant-ID` header, and the
request SHALL require an `X-Account-Id` header.

#### Scenario: Empty body returns tenant meetings with defaults

- **WHEN** a client sends `POST /api/1/meetings` with a valid `X-Tenant-ID`, an
  `X-Account-Id` header, an empty JSON body `{}`, and the caller has
  `view-meeting` permission
- **THEN** the response is `200 OK` containing meetings from that tenant ordered
  by created-at descending with default page size

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends `POST /api/1/meetings`
- **THEN** the response is `403` Problem Details with code `NOT_AUTHORIZED` and
  no meeting list is returned

### Requirement: Creator filter

The system SHALL filter the listing by the optional `creatorId` field. When
`creatorId` is null or omitted, the system SHALL return meetings from every
creator in the tenant. When `creatorId` is present, the system SHALL return only
meetings whose host identity equals `creatorId`. A caller MAY pass its own
`accountId` as `creatorId` to obtain its own meetings.

#### Scenario: No creator filter returns all creators

- **WHEN** a client lists with `creatorId` omitted
- **THEN** the response includes meetings hosted by different accounts in the
  tenant

#### Scenario: Creator filter restricts to one host

- **WHEN** a client lists with `creatorId` set to a specific account id
- **THEN** every returned meeting is hosted by that account, and meetings hosted
  by other accounts are excluded

### Requirement: Status filter

The system SHALL filter the listing by the optional `statuses` field. When
`statuses` is null or empty, the system SHALL return meetings of any status.
When `statuses` contains one or more values, the system SHALL return only
meetings whose status is one of the supplied values.

#### Scenario: No status filter returns all statuses

- **WHEN** a client lists with `statuses` omitted
- **THEN** meetings in `SCHEDULED`, `RUNNING`, `COMPLETED`, and `CANCELED` may
  all appear

#### Scenario: Status filter restricts to supplied statuses

- **WHEN** a client lists with `statuses` = `["SCHEDULED", "RUNNING"]`
- **THEN** every returned meeting has status `SCHEDULED` or `RUNNING`, and none
  is `COMPLETED` or `CANCELED`

### Requirement: Issue filter

The system SHALL filter the listing by the optional `issueKey` field. When
present, the system SHALL return only meetings whose linked Jira issue key
equals `issueKey`.

#### Scenario: Issue filter restricts to one issue key

- **WHEN** a client lists with `issueKey` = `SMISKI-102`
- **THEN** every returned meeting is linked to issue `SMISKI-102`

### Requirement: Text search

The system SHALL apply the optional `search` field as a case-insensitive
substring match against the meeting `title` and the linked Jira `issueKey`. A
meeting SHALL be included when the search text is contained in either field.

#### Scenario: Search matches title substring

- **WHEN** a client lists with `search` = `retro` and a meeting titled
  `Retro follow-up` exists
- **THEN** that meeting is included in the response

#### Scenario: Search matches issue key

- **WHEN** a client lists with `search` = `SMISKI-102`
- **THEN** meetings linked to issue `SMISKI-102` are included even when their
  title does not contain the search text

#### Scenario: Search is case-insensitive

- **WHEN** a client lists with `search` = `RETRO` and a meeting titled
  `Retro follow-up` exists
- **THEN** that meeting is included

### Requirement: Soft-deleted meetings excluded

The system SHALL always exclude meetings that have been soft-deleted
(`deleted_at` is set) from the listing, regardless of any filter.

#### Scenario: Deleted meeting is not listed

- **WHEN** a meeting has been soft-deleted and a client lists meetings
- **THEN** the soft-deleted meeting does not appear in the response

### Requirement: Ordering and sort modes

The system SHALL order results newest-first with a total-order tie-breaker on
the meeting id. When `sort` is `CREATED_AT` (the default), results SHALL be
ordered by creation time descending, then id descending. When `sort` is
`START_TIME`, results SHALL be ordered by the effective start time descending,
then id descending, where the effective start time is the meeting's `startTime`
when present and its creation time when `startTime` is absent (as for INSTANT
meetings).

#### Scenario: Default order is by creation time

- **WHEN** a client lists without specifying `sort`
- **THEN** meetings are returned from most recently created to least recently
  created

#### Scenario: Start-time sort uses creation time for meetings without a start time

- **WHEN** a client lists with `sort` = `START_TIME` and the set includes an
  INSTANT meeting whose `startTime` is null
- **THEN** that meeting is ordered by its creation time relative to the other
  meetings' effective start times, and no meeting is dropped from the results

#### Scenario: Ties are broken deterministically

- **WHEN** two meetings share the same ordering timestamp
- **THEN** they are ordered by descending id and neither is skipped nor
  duplicated across pages

### Requirement: Keyset pagination

The system SHALL paginate results using keyset (cursor) pagination. It SHALL
apply the requested `pageSize`, defaulting to 20 when omitted and rejecting
values greater than 50. The response envelope SHALL contain the page items under
`data`, and a `meta` object with `size` (number of items returned), `hasNext`
(whether more results exist), and `nextPageToken` (the opaque cursor for the
next page, omitted when there are no further results). The system SHALL
determine `hasNext` without computing a total count. A subsequent request
presenting `nextPageToken` SHALL return the following page continuing the same
order without skipping or repeating items from the previous page.

#### Scenario: First page returns items and a next-page token

- **WHEN** more meetings exist than the page size and a client lists the first
  page
- **THEN** the response returns exactly `pageSize` items with `meta.hasNext`
  true and a non-empty `meta.nextPageToken`

#### Scenario: Following the token returns the next page without overlap

- **WHEN** a client submits a follow-up request with the `nextPageToken` from
  the previous page
- **THEN** the response contains the next meetings in order, none of which
  appeared on the previous page

#### Scenario: Last page omits the next-page token

- **WHEN** a client reaches the final page of results
- **THEN** `meta.hasNext` is false and `meta.nextPageToken` is omitted

#### Scenario: Empty result set

- **WHEN** no meetings match the filters
- **THEN** `data` is an empty array, `meta.size` is 0, `meta.hasNext` is false,
  and no next-page token is present

#### Scenario: Page size over the maximum is rejected

- **WHEN** a client lists with `pageSize` greater than 50
- **THEN** the request fails with a `400` Problem Details validation response

### Requirement: Opaque cursor bound to sort field

The system SHALL issue the `nextPageToken` as an opaque, signed token that
encodes both the keyset position and the sort field it was issued for. The
system SHALL reject a token whose signature is invalid or whose contents are
malformed, and SHALL reject a token whose encoded sort field differs from the
current request's `sort`, returning a `400` Problem Details response with code
`INVALID_CURSOR`. Clients SHALL treat such a rejection as an instruction to
restart pagination without a token.

#### Scenario: Tampered token is rejected

- **WHEN** a client submits a `pageToken` whose signature does not verify
- **THEN** the request fails with a `400` Problem Details response with code
  `INVALID_CURSOR`

#### Scenario: Token reused under a different sort is rejected

- **WHEN** a client obtains a `nextPageToken` while sorting by `CREATED_AT` and
  then submits it with `sort` = `START_TIME`
- **THEN** the request fails with a `400` Problem Details response with code
  `INVALID_CURSOR`

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
