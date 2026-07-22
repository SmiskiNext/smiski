## Context

The `meet` service already ships the read-side scaffolding for listing meetings
but never wired it up:

- `MeetingSummary` projection (read model) and a cursor-paginated port method
  `MeetingRepository.findSummariesByHostId(...)` exist, but the adapter throws
  `UnsupportedOperationException`.
- Shared keyset primitives exist and are proven: `CursorPageResponse<T>`,
  `ScrollCursor(createdAt, id)`, `CursorTokenEncoder` + HMAC-SHA256
  `CursorEncoder` (secret from `app.cursor.secret`), and the list envelope
  `PageResponse<T>` / `PageMeta` (which already supports a cursor variant with
  `size`, `hasNext`, `nextPageToken`).
- The `meetings` table is `PARTITION BY HASH (tenant_id)` (16 partitions) with
  `tenant_id` leading every key. Tenancy is enforced automatically by Hibernate
  `@TenantId` on `MeetingJpaEntity`, which appends `WHERE tenant_id = ?` to
  every query and enables partition pruning.
- Relevant existing indexes (all `WHERE deleted_at IS NULL`):
  `idx_meetings_keyset (tenant_id, created_at DESC, id DESC)`,
  `idx_meetings_host (tenant_id, host_id)`,
  `idx_meetings_status (tenant_id, status)`,
  `idx_meetings_issue (tenant_id, issue_id)`.

The UI ("All meetings" panel) needs: a text search box, status filter, issue
filter, creator filter, a HOST column, an Issue column, and a "Schedule /
Started" column that can be sorted.

Primary keys are UUIDv7 (time-ordered), so `id` is a valid monotonic tie-breaker
aligned with `created_at`.

## Goals / Non-Goals

**Goals:**

- One endpoint that lists tenant meetings with optional
  creator/status/issue/text filters and stable keyset pagination.
- Two sort modes: `CREATED_AT` (default) and `START_TIME`, both newest-first.
- Correct, tamper-resistant opaque cursors that cannot silently produce wrong
  pages when the sort mode changes.
- Reuse the existing shared keyset and envelope primitives; keep the domain
  framework-agnostic; obey the hexagonal layering enforced by ArchUnit.

**Non-Goals:**

- Response caching (Valkey/`@Cacheable`/eviction). The keyset indexes make the
  query fast; caching a filtered, cursor-paginated, high-churn list has poor hit
  rates and heavy invalidation. Documented as a future optimization.
- Trigram / `pg_trgm` full-text search; `ILIKE '%q%'` is acceptable at single-
  tenant scale.
- Admin-only authorization for tenant-wide listing (all authenticated tenant
  users may list; no per-row ACL).
- Listing participated meetings (`findParticipatedSummariesByAccountId` stays a
  stub) and sorting by any column other than created/start time.

## Decisions

### D1: POST `/api/1/meetings`, not GET or QUERY

The request carries several optional filters plus a cursor and is best expressed
as a JSON body. The HTTP `QUERY` method (RFC 10008) is the semantically ideal
choice (safe, idempotent, body-bearing) but only reached Proposed Standard in
June 2026 and has **no native Spring Boot 4 support** (`RequestMethod` lacks a
`QUERY` constant; spring-framework issue #36988 / PR #34993 open as of
mid-2026). Adopting it now would require framework hacks and breaks
springdoc/gateways.

Chosen: `POST /meetings` with a body. This matches the repo's existing action
idiom (`POST /meetings:instant`, `POST /meetings:schedule`) and is understood by
all tooling today. `POST /meetings` (flat path) is unused for creation in this
service, so there is no REST collision. When Spring adds native `QUERY` support,
switching the mapping is a one-line change.

Alternatives: GET with query params (awkward for `statuses` arrays and a long
cursor, and no body); QUERY (not production-ready).

### D2: Single `creatorId` filter (no `scope` enum)

"My meetings", "one creator's meetings", and "all meetings" differ only by a
`host_id` predicate. Because the client always sends `X-Account-Id`, it can pass
its own id to get "my meetings". A separate `scope` enum would be redundant with
`creatorId`.

Chosen: nullable `creatorId`. `null` = no creator predicate (all creators in the
tenant, matching the UI default "All creators" / "All meetings"); a value
filters to that `host_id`.

Alternatives: `scope=MINE|ALL` + `creatorId` (redundant, and cannot express
`ALL` and a default `MINE` with one field without a dirty sentinel).

### D3: Dual-mode keyset ordering with null-safe start time

Ordering uses a strict row-value tuple comparison so pagination is stable and
index-friendly:

- `CREATED_AT`: `ORDER BY created_at DESC, id DESC`, keyset predicate
  `(created_at, id) < (:ts, :id)`.
- `START_TIME`: INSTANT meetings have `start_time = NULL`, so the sort key is
  `COALESCE(start_time, created_at)`.
  `ORDER BY COALESCE(start_time, created_at) DESC, id DESC`, keyset predicate
  `(COALESCE(start_time, created_at), id) < (:ts, :id)`.

`hasNext` is detected by fetching `pageSize + 1` rows and trimming the probe
row; no `COUNT(*)`/total is computed. `id` (UUIDv7) is the total-order
tie-breaker in both modes.

Alternatives: `created_at`-only cursor (not a total order — rows sharing a
timestamp can be skipped/duplicated); `OFFSET` (drifts on insert, slows on deep
pages); `NULLS LAST` on raw `start_time` (needs extra cursor state to represent
the null boundary — `COALESCE` folds it into one comparable value).

### D4: Sort-tagged, signed cursor token

The shared `CursorEncoder` signs `(Instant, UUID)` but carries no sort context.
With two sort modes, a token issued under one sort must not be replayed under
another (it would compare against the wrong column and silently return wrong
rows).

Chosen: a meet-local cursor codec that wraps the shared `CursorEncoder` and
prepends a one-character sort tag to the signed payload (e.g. `C`/`S`). Decoding
verifies the HMAC (via the shared encoder) and returns both the `ScrollCursor`
position and the sort field. If the decoded sort tag differs from the request's
`sort`, or the token is malformed/tampered, the use case returns
`CursorErrorCode.INVALID_CURSOR` (→ 400 problem+json). This keeps the shared
component untouched (it is used by other services) while satisfying the
production requirement to bind cursors to their sort field.

Alternatives: extend shared `ScrollCursor`/`CursorTokenEncoder` to carry a sort
enum (wider blast radius across services); embed a full filter fingerprint
(over-engineering — only the sort field changes the comparison column).

### D5: Dynamic query construction

Filters are optional and combinable. The adapter builds the query dynamically
(JPA Criteria API or a parameterized native query) with tenant scoping applied
automatically by `@TenantId`. `COALESCE`, row-value tuple comparison, and the
`ILIKE` search are expressed against the mapped entity/columns. The exact
mechanism (Criteria vs native) is an implementation detail chosen during coding;
correctness (predicate combinations, keyset tuple, ordering) is fixed by the
spec scenarios.

### D6: Indexing

Add partial indexes (`WHERE deleted_at IS NULL`) in migration
`V2__meetings_search_indexes.sql`:

- `(tenant_id, COALESCE(start_time, created_at) DESC, id DESC)` — expression
  index for the `START_TIME` sort keyset.
- `(tenant_id, host_id, created_at DESC, id DESC)` — for the `creatorId` filter
  combined with the default sort.

Tenant-wide `CREATED_AT` sorting already uses `idx_meetings_keyset`. Every query
includes `tenant_id`, so partition pruning keeps scans within one partition.

## Risks / Trade-offs

- `ILIKE '%q%'` cannot use a B-tree index → sequential scan within the tenant
  partition. → Acceptable at per-tenant scale; documented `pg_trgm` GIN index as
  a future optimization.
- Client changes `sort` mid-pagination while reusing an old `pageToken`. →
  Cursor is sort-tagged; mismatch returns `INVALID_CURSOR` so the client
  restarts cleanly rather than getting silently wrong pages.
- `start_time` is mutable (reschedule), so a `START_TIME`-sorted page boundary
  can shift if a meeting is rescheduled between page fetches. → Inherent to
  keyset on mutable keys; `id` tie-breaker prevents duplicates/skips within a
  stable key, and this matches the accepted behavior of keyset pagination on
  mutable sort columns.
- Rotating `app.cursor.secret` invalidates in-flight tokens. → Pre-existing
  shared behavior; clients treat `INVALID_CURSOR` as "start from the beginning".
- Expression index must exactly match the
  `COALESCE(start_time, created_at) DESC, id DESC` ordering or it will not be
  used. → Verified via integration test / `EXPLAIN` during implementation.

## Migration Plan

1. Ship `V2__meetings_search_indexes.sql` (additive `CREATE INDEX`; no data
   change, no rewrite of applied migrations). Forward-only; rollback is dropping
   the new indexes, which only affects query plans, not correctness.
2. Deploy the meet service. The new endpoint is additive; no existing endpoint
   or contract changes.
3. Regenerate `services/meet/openapi.yaml` from the OpenAPI generation test.

## Open Questions

None. All contract, ordering, filtering, and cursor decisions are resolved
above.
