# list-pending-join-requests Specification

## Purpose

TBD - created by archiving change list-pending-join-requests. Update Purpose
after archive.

## Requirements

### Requirement: Host can retrieve pending join requests

The system SHALL expose `GET /meetings/{id}/join-requests` so the host of a
meeting with `MANUAL_APPROVAL` admission policy can retrieve all `PENDING` join
requests for that meeting, paginated by offset.

The endpoint SHALL accept optional query parameters `offset` (default `0`, min
`0`) and `pageSize` (default `20`, min `1`, max `100`).

The response body SHALL contain an array `results` of pending join request
summaries and a `meta` object with `total`, `offset`, and `pageSize`.

Each summary item SHALL include: `requestId` (UUID string), `accountId`,
`displayName`, `status` (always `"PENDING"`), `requestedAt` (ISO-8601 instant),
and `expiresAt` (ISO-8601 instant).

Only the meeting's host (the account whose `accountId` matches `meeting.hostId`)
SHALL receive a `200 OK` response. Any other authenticated tenant member SHALL
receive `403 NOT_OWNER`.

#### Scenario: Host lists pending requests — meeting has waiters

- **WHEN** the host sends `GET /meetings/{id}/join-requests` for a `RUNNING`
  meeting with `MANUAL_APPROVAL` and three `PENDING` join requests
- **THEN** the response is `200 OK` with `results` containing three items
  ordered by `requestedAt` ascending and `meta.total` equal to `3`

#### Scenario: Host lists pending requests — empty queue

- **WHEN** the host sends `GET /meetings/{id}/join-requests` for a meeting with
  no pending requests (policy is `ALLOW_ALL` or queue is empty)
- **THEN** the response is `200 OK` with `results` as an empty array and
  `meta.total` equal to `0`

#### Scenario: Offset pagination — second page

- **WHEN** the host sends `GET /meetings/{id}/join-requests?offset=2&pageSize=2`
  and the queue has five pending requests
- **THEN** the response is `200 OK`, `results` contains two items (items at
  index 2 and 3), and `meta.offset` is `2`

#### Scenario: Non-host caller is rejected

- **WHEN** an authenticated tenant member who is not the meeting host sends
  `GET /meetings/{id}/join-requests`
- **THEN** the response is `403 application/problem+json` with `code`
  `NOT_OWNER`

#### Scenario: Meeting not found

- **WHEN** the caller sends `GET /meetings/{id}/join-requests` where `{id}` does
  not correspond to an active meeting in the caller's tenant
- **THEN** the response is `404 application/problem+json` with `code`
  `MEETING_NOT_FOUND`

#### Scenario: Missing account header

- **WHEN** the caller sends `GET /meetings/{id}/join-requests` without the
  `X-Account-Id` header
- **THEN** the response is `400 application/problem+json` with `code`
  `VALIDATION_ERROR`

#### Scenario: Page size out of range

- **WHEN** the host sends `GET /meetings/{id}/join-requests?pageSize=0` or
  `?pageSize=101`
- **THEN** the response is `400 application/problem+json` with `code`
  `VALIDATION_ERROR`
