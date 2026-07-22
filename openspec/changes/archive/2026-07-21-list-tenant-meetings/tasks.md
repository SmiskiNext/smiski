# Implementation Tasks

## 1. Domain read model and port

- [x] 1.1 Add `issueKey` field to `MeetingSummary` projection
- [x] 1.2 Add `MeetingSortField` enum (`CREATED_AT`, `START_TIME`) in
      `domain/model` (or `domain/projection`)
- [x] 1.3 Add `MeetingSearchCriteria` value type carrying `creatorId` (nullable
      `AccountId`), `search` (nullable), `statuses` (`Set<MeetingStatus>`),
      `issueKey` (nullable), `sort` (`MeetingSortField`), and the decoded keyset
      position (nullable `sortValue` + `id`)
- [x] 1.4 Replace `MeetingRepository.findSummariesByHostId(...)` with
      `searchSummaries(MeetingSearchCriteria criteria, int pageSize)` returning
      `CursorPageResponse<MeetingSummary>`; leave
      `findParticipatedSummariesByAccountId` untouched ← (verify: port is
      framework-agnostic, no Spring/JPA imports; ArchUnit passes)

## 2. Application layer

- [x] 2.1 Add `ListMeetingsQuery` (implements shared `Query`) with the raw
      request inputs plus tenant/account context
- [x] 2.2 Add `ListMeetingsUseCase` interface extending
      `UseCase<ListMeetingsQuery, ListMeetingsResult, ...>`
- [x] 2.3 Add `ListMeetingsResult` (framework-agnostic) carrying the page items,
      `hasNext`, and the encoded `nextPageToken`
- [x] 2.4 Add mapper(s) `MeetingSummary` → result item
- [x] 2.5 Implement `ListMeetingsApplicationService`: clamp/validate `pageSize`,
      decode `pageToken` (reject on invalid/tampered/sort-mismatch →
      `INVALID_CURSOR`), build `MeetingSearchCriteria`, call `searchSummaries`,
      and encode `nextPageToken` from the last item when `hasNext` ← (verify:
      default pageSize 20, max 50 enforced; cursor decode failures and
      sort-mismatch map to INVALID_CURSOR; nextPageToken omitted on last page)

## 3. Infrastructure — cursor codec

- [x] 3.1 Add a meet-local cursor codec that wraps the shared `CursorEncoder`,
      prepending a sort tag so encode/decode carry the `MeetingSortField`;
      decode verifies the HMAC and returns position + sort field ← (verify:
      token round-trips per sort mode; wrong-sort and tampered tokens fail;
      shared CursorEncoder is not modified)

## 4. Infrastructure — persistence

- [x] 4.1 Implement `MeetingRepositoryAdapter.searchSummaries` with a dynamic,
      tenant-scoped query applying optional creator/status/issue/search
      predicates and always excluding `deleted_at IS NOT NULL`
- [x] 4.2 Apply keyset ordering per sort mode: `CREATED_AT` →
      `(created_at, id)`; `START_TIME` →
      `(COALESCE(start_time, created_at), id)`, both descending with strict
      row-value tuple comparison
- [x] 4.3 Fetch `pageSize + 1` rows, compute `hasNext`, trim the probe row, and
      map to `MeetingSummary` (including `issueKey`) ← (verify: no COUNT(*);
      tuple comparison correct; ordering matches spec for both sort modes
      including null start_time)
- [x] 4.4 Add Flyway migration `V2__meetings_search_indexes.sql` creating
      partial indexes (`WHERE deleted_at IS NULL`):
      `(tenant_id, COALESCE(start_time, created_at) DESC, id DESC)` and
      `(tenant_id, host_id, created_at DESC, id DESC)` ← (verify: migration
      applies on a clean DB; entity/schema stay in sync; START_TIME query uses
      the expression index)

## 5. Presentation layer

- [x] 5.1 Add `ListMeetingsRequest` DTO (Jakarta `@Valid`; `pageSize` max 50;
      `sort` enum; `statuses` enum) with `toQuery(tenantId, accountId)`
- [x] 5.2 Add `MeetingSummaryResponse` DTO with a static `from(...)` factory
      producing the summary shape (no tenant id)
- [x] 5.3 Add the `@PostMapping("/meetings")` handler to `MeetingController`:
      require `X-Account-Id` (400 problem+json when missing), resolve tenant,
      invoke the use case, and return `PageResponse<MeetingSummaryResponse>` via
      the cursor envelope ← (verify: endpoint path is /api/1/meetings; success
      returns data+meta; INVALID_CURSOR and validation errors return RFC 9457
      problem+json)

## 6. Tests

- [x] 6.1 Domain/application unit tests: `pageSize` clamping and max-50
      rejection, criteria building, cursor decode/sort-mismatch →
      `INVALID_CURSOR`, `nextPageToken` presence/absence (mocked port)
- [x] 6.2 Cursor codec unit tests: round-trip per sort mode, tampered token,
      wrong-sort rejection
- [x] 6.3 Persistence integration test (Testcontainers): tenant isolation, each
      filter, combined filters, soft-delete exclusion, both sort modes
      (including null start_time), keyset paging with no overlap, empty result
- [x] 6.4 Controller integration test: empty body defaults, missing account
      header, page-size validation, next-page-token follow, `INVALID_CURSOR`
      response ← (verify: all spec scenarios covered across the three test
      layers)

## 7. API spec and formatting

- [x] 7.1 Run `generateOpenApiDocsFromTests` and commit the regenerated
      `services/meet/openapi.yaml`
- [x] 7.2 Run `spotlessApply`, then `./services/gradlew -p services/meet test`
      and `integrationTest`; fix any failures ← (verify: build green;
      openapi.yaml includes POST /meetings with the documented request/response
      schemas)
