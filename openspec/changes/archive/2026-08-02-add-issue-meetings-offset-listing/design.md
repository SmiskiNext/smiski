## Context

The Jira issue panel (frontend UC02/UC07) shows the meetings linked to the
currently open issue. Today the frontend calls `POST /api/1/meetings` with an
`issueKey` filter (`listIssueMeetings` in
`app/static/smiski-ui/src/api/meetings.ts`) and receives a keyset/cursor page.
Two constraints motivate a new endpoint:

- The panel wants offset-style navigation (jump to page N, show a total count),
  which keyset pagination deliberately does not provide (`list-tenant-meetings`
  computes `hasNext` without a total).
- The issue linkage is most cheaply queried by the stable numeric `issueId`
  column, which is already covered by `idx_meetings_issue(tenant_id, issue_id)`
  (`services/meet/src/main/resources/db/migration/B1.0.0__baseline.sql:152`).

The `meet` service already contains a working offset-pagination reference —
`GET /meetings/{id}/join-requests` (`list-pending-join-requests`) — built on the
shared `OffsetPageResponse<T>` domain primitive and a
`{results, meta{total, offset, pageSize}}` response. The shared web layer also
exposes `PageResponse.offset(...)` / `PageMeta.offset(...)`, but that meta shape
carries `size/hasNext/offset/nextOffset` and no `total`.

Constraints from `api-convention` and `services/AGENTS.md`:

- Global `/api/{version}` prefix; controllers declare only the resource path.
- Hexagonal layering `domain → application → infrastructure → presentation`,
  ArchUnit-enforced naming (`*UseCase`, `*ApplicationService`,
  `*RepositoryAdapter`).
- Success bodies carry no tenant identifier; errors are RFC 9457 problem+json.
- Tenant isolation is applied automatically by the persistence layer
  (`@TenantId`), so criteria never carry `tenant_id`.

## Goals / Non-Goals

**Goals:**

- Add `POST /api/1/issues/{issueId}/meetings` returning an offset page of the
  meetings linked to `issueId` in the caller's tenant.
- Return a `total` count so the panel can render page numbers.
- Reuse the existing `MeetingSummary` projection and `toSummary` mapping.
- Reuse the `issue_id` index; keep the query single-partition per tenant.
- Match the existing offset-pagination reference's structure and validation
  behavior (default/min/max page size, problem+json errors).

**Non-Goals:**

- No change to `POST /meetings` (`list-tenant-meetings`) or its cursor scheme.
- No frontend or generated-SDK changes; `listIssueMeetings` keeps using the
  existing `issueKey` path until a separate change rewires it.
- No change to shared paging classes (`OffsetPageResponse`, `PageResponse`,
  `PageMeta`).
- No new filters (status, creator, search) — this endpoint filters by `issueId`
  only.
- No database migration — the required column and index already exist.

## Decisions

### Decision 1: `POST` on a nested collection means "list"

`POST /api/1/issues/{issueId}/meetings` lists meetings; it does not create one.

- **Rationale**: This service already overloads `POST` on a collection to mean
  list/search (`POST /meetings` is the tenant listing). Following that precedent
  keeps the two listing endpoints consistent, and a request body is the natural
  place for pagination inputs in this codebase.
- **Alternatives considered**:
    - `GET /issues/{issueId}/meetings?offset=&pageSize=` — most REST-idiomatic
      and matches the `join-requests` offset endpoint (which is a `GET`), but
      the user explicitly chose `POST`.
    - `GET /meetings?issueId=&offset=&pageSize=` — keeps everything under the
      owned `meetings` resource, but collides on the `/meetings` path with the
      existing cursor `POST` and mixes two pagination styles on one path.
- **Trade-off**: A reader skimming the route list may misread
  `POST .../meetings` as "create a meeting under an issue". Mitigated by an
  explicit `@Operation` summary/description and the proposal note.

### Decision 2: Bespoke response envelope carrying `total`

Introduce
`IssueMeetingListPageResponse { data: MeetingSummary[], meta: { total, offset, pageSize, hasNext } }`
in the presentation layer.

- **Rationale**: The panel needs `total` (Q1 = B). The shared `PageMeta.offset`
  has no `total`, and extending it would ripple into every service's emitted
  OpenAPI. A dedicated response keeps the change contained to `meet` and mirrors
  the existing `ListPendingJoinRequestsResponse` meta trio, adding `hasNext` and
  keeping the `data` key used by the meetings listing.
- **Alternatives considered**:
    - Extend shared `PageMeta`/`PageResponse.offset` with `total` — cleaner
      long-term but forces OpenAPI regeneration across `tenant`/`record`/etc.
      and was explicitly declined.
    - Reuse `PageResponse.offset` as-is — rejected: cannot express `total`.
- **Trade-off**: A third page-meta shape now exists in `meet` (cursor
  `data/meta`, join-requests `results/meta`, this `data/meta`). Accepted to
  avoid touching shared code.

### Decision 3: Offset query via `JpaSpecificationExecutor` + offset `Pageable`

Add
`MeetingRepository#findSummariesByIssueId(String issueId, int offset, int pageSize)`
returning `OffsetPageResponse<MeetingSummary>` plus a `total`. The adapter
builds a `notDeleted() AND issueIdIs(issueId)` specification, sorts
`createdAt DESC, id DESC`, and pages with a raw-offset `Pageable` (offset is a
row index, not `offset/pageSize`), reusing the existing `toSummary` mapper.

- **Rationale**: `JpaSpecificationExecutor.findAll(spec, pageable)` returns a
  `Page` whose `getTotalElements()` supplies `total` and whose `hasNext()`
  supplies the flag — one count + one data query, both pruned to a single
  partition by the tenant-leading index. `issue_id` is exactly indexed.
- **Alternatives considered**:
    - Extend the existing keyset `searchSummaries(...)` with an offset mode —
      rejected: conflates two pagination strategies in one method.
    - A hand-written JPQL `@Query` with `countQuery` — viable, but the
      Specification approach reuses `notDeleted()` and `toSummary` already
      present in the adapter.
- **Trade-off**: Deep offsets scan-and-discard preceding rows (classic offset
  cost). Acceptable: meetings-per-issue is a small, bounded set.

### Decision 4: Result type reuses the read-side error, use case never fails it

`ListIssueMeetingsUseCase extends UseCase<ListIssueMeetingsQuery, ListIssueMeetingsResult, ListMeetingsError>`.
There is no meeting-ownership or not-found check (the path filters by `issueId`;
an unknown issue is simply an empty page), and `pageSize`/`offset` bounds are
validated at the controller.

- **Rationale**: Keeps the read-side error vocabulary consistent with
  `list-tenant-meetings`. Unlike `join-requests`, there is no host-ownership
  gate, so no `MeetingError` path is needed.
- **Alternatives considered**: A dedicated empty error type — unnecessary
  ceremony for a listing that cannot fail on the domain side.

### Flow

```mermaid
sequenceDiagram
    participant C as Client (issue panel)
    participant Ctl as MeetingController
    participant UC as ListIssueMeetingsApplicationService
    participant Repo as MeetingRepository (port)
    participant Ad as MeetingRepositoryAdapter (JPA)

    C->>Ctl: POST /api/1/issues/{issueId}/meetings {offset,pageSize}
    Note over Ctl: require X-Account-Id, view-meeting; validate offset>=0, 1<=pageSize<=50
    Ctl->>UC: execute(ListIssueMeetingsQuery)
    UC->>Repo: findSummariesByIssueId(issueId, offset, pageSize)
    Repo->>Ad: findAll(notDeleted AND issueIdIs, offset Pageable)
    Ad-->>Repo: Page<MeetingSummary> (items + totalElements + hasNext)
    Repo-->>UC: OffsetPageResponse + total
    UC-->>Ctl: Result.success(ListIssueMeetingsResult)
    Ctl-->>C: 200 {data:[...], meta:{total,offset,pageSize,hasNext}}
```

## Risks / Trade-offs

- **`POST`-as-list misread as create** → Mitigated by explicit `@Operation` docs
  and the proposal note; consistent with the existing `POST /meetings`.
- **Third page-meta shape in `meet`** → Accepted to keep shared paging classes
  untouched; documented in Decision 2.
- **Deep-offset scan cost** → Acceptable for the small meetings-per-issue set;
  the tenant-leading `issue_id` index keeps each page single-partition.
- **`total` drift under concurrent writes** → The count and page run in the same
  read-only transaction; a meeting created between a client's page requests may
  shift offsets (inherent to offset pagination). Acceptable for this UI.
- **`issueId` value mismatch** → The filter matches the stored `issue_id` string
  exactly; callers must pass the numeric Jira issue id, not the issue key.
  Documented in the spec.

## Migration Plan

No data migration. Deploy is additive: the new endpoint appears once `meet` is
built and the regenerated `services/meet/openapi.yaml` is published. Rollback is
removal of the endpoint and its artifacts; no persisted state changes.

## Open Questions

None — endpoint shape, response envelope, page-size bounds, sort, and scope were
resolved during exploration.
