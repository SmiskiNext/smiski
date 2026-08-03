## 1. Domain port

- [x] 1.1 Add `findSummariesByIssueId(String issueId, int offset, int pageSize)`
      to `MeetingRepository`, returning `OffsetPageResponse<MeetingSummary>`,
      with a doc comment noting tenant scoping is applied automatically and
      soft-deleted meetings are excluded
- [x] 1.2 Add `countByIssueId(String issueId)` to `MeetingRepository` (or
      document that the offset page carries the total) so the total is available
      to the application layer ← (verify: port method signatures match how the
      application service consumes them; return type reuses the shared
      `OffsetPageResponse<MeetingSummary>`)

## 2. Infrastructure adapter

- [x] 2.1 Add an `issueIdIs(String issueId)` specification to
      `MeetingRepositoryAdapter` (exact match on `issueId`, combined with the
      existing `notDeleted()`)
- [x] 2.2 Implement `findSummariesByIssueId` in `MeetingRepositoryAdapter` using
      `JpaSpecificationExecutor.findAll(spec, pageable)` with a raw-offset
      `Pageable` (offset is a row index), sort `createdAt DESC, id DESC`,
      reusing the existing `toSummary` mapper; derive `total` from
      `Page.getTotalElements()` and `hasNext` from the page/total ← (verify:
      offset is treated as a row index not a page number; ordering is
      `createdAt DESC, id DESC`; soft-deleted rows excluded; query uses
      `issue_id` so the tenant-leading index applies)

## 3. Application layer

- [x] 3.1 Create `ListIssueMeetingsQuery` (record implementing `Query`) with
      `issueId`, `tenantId`, `accountId`, `offset`, `pageSize`
- [x] 3.2 Create `ListIssueMeetingsResult` (record) carrying the page items
      (reuse `ListMeetingsResult.Item`), plus `total`, `offset`, `pageSize`,
      `hasNext`
- [x] 3.3 Create `ListIssueMeetingsUseCase` interface extending
      `UseCase<ListIssueMeetingsQuery, ListIssueMeetingsResult, ListMeetingsError>`
- [x] 3.4 Create `ListIssueMeetingsApplicationService`
      (`@Service @Transactional(readOnly = true)`) implementing the use case:
      call the repository, map summaries to result items via
      `MeetingSummaryMapper`, and return `Result.success` ← (verify:
      `offset`/`pageSize` are passed through unchanged from the query;
      `total`/`hasNext` come from the repository page; mapping reuses
      `MeetingSummaryMapper::toItem`)

## 4. Presentation layer

- [x] 4.1 Create `ListIssueMeetingsRequest` (record) with `@Min(0)` `offset` and
      `@Min(1) @Max(50)` `pageSize` (both nullable, defaulting to 0 and 20),
      plus `toQuery(issueId, tenantId, accountId)`
- [x] 4.2 Create `IssueMeetingListPageResponse` with
      `data: List<MeetingSummaryResponse>` and
      `meta { total, offset, pageSize, hasNext }`, plus a static
      `from(ListIssueMeetingsResult)` factory reusing
      `MeetingSummaryResponse::from`
- [x] 4.3 Add the `POST /issues/{issueId}/meetings` handler to
      `MeetingController`: inject `ListIssueMeetingsUseCase`, require
      `X-Account-Id` (400 problem+json when missing), guard `pageSize`/`offset`
      bounds (400 `VALIDATION_ERROR`), resolve tenant from `TenantContext`, map
      the result via `IssueMeetingListPageResponse::from`, and annotate
      `@PreAuthorize("hasAuthority('view-meeting')")` ← (verify: route is
      `/issues/{issueId}/meetings` under the global `/api/{version}` prefix;
      POST is documented as "list"; body-less/invalid inputs produce the
      documented problem+json responses)
- [x] 4.4 Add `@Operation`/`@ApiResponses` OpenAPI docs on the handler: 200
      (`IssueMeetingListPageResponse` example with a non-empty page), 400
      (missing account + page-size/offset out of range examples referencing
      `ProblemDetail`) ← (verify: success example conforms to the response
      schema; nullable fields marked nullable; 400 examples cover both
      `VALIDATION_ERROR` cases)

## 5. Tests — offset listing behavior (from spec scenarios)

- [x] 5.1 Application unit test: issue with meetings returns the requested page,
      `total` equals the issue's non-deleted meeting count, items ordered
      `createdAt DESC` (mock the repository port)
- [x] 5.2 Application unit test: issue with no meetings returns empty `data` and
      `total = 0` (not a 404)
- [x] 5.3 Application unit test: `offset` beyond `total` returns empty `data`
      with the real `total` and `hasNext = false`
- [x] 5.4 Adapter integration test (Testcontainers): `findSummariesByIssueId`
      pages by row offset, computes `total`/`hasNext`, orders
      `createdAt DESC, id DESC`, filters to the given `issueId`, and excludes
      soft-deleted meetings ← (verify: matches design Decision 3 — offset is a
      row index, single `issueId` filter, soft-delete exclusion)
- [x] 5.5 Controller integration test: `POST /issues/{issueId}/meetings` returns
      200 with the `{data, meta{total,offset,pageSize,hasNext}}` envelope for a
      populated issue
- [x] 5.6 Controller integration test: second-page request (`offset`/`pageSize`)
      returns the correct slice and echoes `meta.offset`
- [x] 5.7 Controller integration test: missing `X-Account-Id` → 400 problem+json
      `VALIDATION_ERROR`
- [x] 5.8 Controller integration test: `pageSize = 0` and `pageSize = 51` → 400
      problem+json `VALIDATION_ERROR`; `offset = -1` → 400 ← (verify: bounds
      match the spec — min offset 0, pageSize 1..50)

## 6. API spec regeneration & verification

- [x] 6.1 Run `./services/gradlew -p services/meet generateOpenApiDocsFromTests`
      and confirm the new operation appears in `services/meet/openapi.yaml`
- [x] 6.2 Run root `pnpm run openapi` to regenerate + lint the meet spec
- [x] 6.3 Run `./services/gradlew spotlessApply` then
      `./services/gradlew -p services/meet test integrationTest` and confirm
      green ← (verify: ArchUnit naming passes for the new
      `*UseCase`/`*ApplicationService`; all new tests pass; OpenAPI lint clean)
