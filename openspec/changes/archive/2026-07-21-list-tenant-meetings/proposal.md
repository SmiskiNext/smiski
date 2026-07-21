## Why

A Jira user viewing the meeting panel needs to browse the meetings that exist in
their tenant — search by name or issue, filter by status/creator/issue, and page
through results — as shown in the "All meetings" list UI. The `meet` service
already declares a cursor-paginated query port
(`MeetingRepository.findSummariesByHostId`) and read model (`MeetingSummary`),
plus shared keyset primitives (`CursorPageResponse`, `ScrollCursor`,
`CursorEncoder`), but the adapter throws `UnsupportedOperationException` and no
endpoint exists. This change delivers the read/list vertical slice.

## What Changes

- Add `POST /api/1/meetings` returning a keyset (cursor) paginated list of
  meetings in the caller's tenant. The endpoint uses **POST**, not GET or the
  new HTTP `QUERY` method: `QUERY` (RFC 10008) only reached Proposed Standard in
  June 2026 and is not natively supported by Spring Framework 7 / Spring Boot 4
  (`RequestMethod` has no `QUERY` constant; issue #36988 / PR #34993 remain
  open), so a body-bearing search endpoint uses the repo's action idiom today.
- Request body (all fields optional): `creatorId` (null = every creator in the
  tenant; a value filters to that host — passing one's own accountId yields "my
  meetings"), `search` (case-insensitive substring over `title` and
  `issue_key`), `statuses` (subset of `SCHEDULED|RUNNING|COMPLETED|CANCELED`;
  null/empty = all), `issueKey` (exact match), `sort` (`CREATED_AT` default, or
  `START_TIME`), `pageSize` (default 20, max 50), and `pageToken` (opaque
  cursor).
- Soft-deleted meetings (`deleted_at IS NOT NULL`) are always excluded.
- Ordering is newest-first with a total-order tie-breaker: `CREATED_AT` sorts by
  `(created_at DESC, id DESC)`; `START_TIME` sorts by
  `(COALESCE(start_time, created_at) DESC, id DESC)` because INSTANT meetings
  have a null `start_time`.
- Keyset pagination: strict row-value tuple comparison
  `(sortValue, id) < (?, ?)`, fetch `pageSize + 1` rows to compute `hasNext`, no
  `COUNT(*)`.
- The opaque `pageToken` is HMAC-signed and embeds the sort field it was issued
  for; presenting a token whose sort field differs from the current request
  fails with `INVALID_CURSOR`, and a malformed/tampered token likewise fails.
- Response is the shared `PageResponse` envelope: `data[]` of meeting summaries
  plus `meta { size, hasNext, nextPageToken }`. Each summary carries `id`,
  `hostId`, `shortCode`, `title`, `description`, `issueKey`, `type`, `status`,
  `startTime`, `endTime`, `createdAt`, and `settings`.
- Replace the unused `MeetingRepository.findSummariesByHostId` stub with a
  criteria-driven `searchSummaries(criteria, pageSize)` port and implement the
  adapter with a dynamic, tenant-scoped keyset query. `MeetingSummary` gains an
  `issueKey` field.
- Add a Flyway migration adding two partial indexes to support the two sort
  modes and the creator filter efficiently.

## Capabilities

### New Capabilities

- `list-tenant-meetings`: Listing meetings within a tenant — the search endpoint
  contract, creator/status/issue/text filtering, dual-mode keyset ordering with
  null-safe start-time handling, opaque signed cursor tokens bound to their sort
  field, the paginated response envelope and summary shape, and tenant scoping.

### Modified Capabilities

<!-- None. No existing capability's requirements change; this is a new read slice. -->

## Impact

- **New endpoint**: `POST /api/1/meetings` (meet service) — regenerates
  `services/meet/openapi.yaml`.
- **meet domain**: `MeetingSummary` projection gains `issueKey`; new
  `MeetingSortField` enum and `MeetingSearchCriteria`; `MeetingRepository`
  replaces `findSummariesByHostId` with `searchSummaries`. The unused
  `findParticipatedSummariesByAccountId` stub is untouched.
- **meet application**: new `ListMeetingsQuery`, `ListMeetingsUseCase`,
  `ListMeetingsApplicationService`, `ListMeetingsResult`, and mapper.
- **meet infrastructure**: dynamic keyset query in `MeetingRepositoryAdapter`; a
  meet-local cursor codec wrapping the shared `CursorEncoder` to tag the sort
  field; new migration `V2__meetings_search_indexes.sql` adding
  `(tenant_id, COALESCE(start_time, created_at) DESC, id DESC)` and
  `(tenant_id, host_id, created_at DESC, id DESC)` partial indexes
  (`WHERE deleted_at IS NULL`). The existing `idx_meetings_keyset` already
  covers tenant-wide `CREATED_AT` sorting.
- **meet presentation**: new `ListMeetingsRequest`, `MeetingSummaryResponse`,
  and a controller handler returning `PageResponse<MeetingSummaryResponse>`.
- **Out of scope**: response caching (Valkey/`@Cacheable`) — the endpoint relies
  on the keyset indexes; participated-meetings listing; trigram/`pg_trgm`
  full-text search; admin-only authorization for tenant-wide listing; sorting by
  columns other than created/start time.
