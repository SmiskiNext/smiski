## Context

The `meet` service already supports `MANUAL_APPROVAL` admission for meetings.
When a participant calls `POST /meetings/{id}:join` against a meeting with this
policy, a `JoinRequest` aggregate (stored in Redis with a TTL of 5 minutes) is
created with status `PENDING`. The host may then call
`POST /meetings/{id}/join-requests:accept` or `:decline` to action individual
requests.

The missing piece is discovery: the host has no HTTP endpoint to retrieve the
pending queue.
`JoinRequestRepository.findPendingSummariesByMeetingId(UUID, int offset, int pageSize)`
already returns an `OffsetPageResponse<JoinRequestSummary>` — the infrastructure
is fully implemented. Only the application and presentation layers need to be
wired.

## Goals / Non-Goals

**Goals:**

- Expose `GET /meetings/{id}/join-requests` returning an offset-paginated list
  of `PENDING` join requests for the host.
- Enforce host-only access (meeting not found → 404, non-host → 403, missing
  header → 400).
- Follow the existing hexagonal DDD layering:
  `Query → UseCase → ApplicationService → Repository → Response`.

**Non-Goals:**

- This change does NOT add `avatarUrl` to `JoinRequestSummary` (Redis schema
  unchanged).
- This change does NOT support filtering by status other than `PENDING`.
- This change does NOT introduce cursor-based pagination for join requests.

## Decisions

### D1 — HTTP method: GET with query parameters

`GET /meetings/{id}/join-requests?offset=0&pageSize=20`

Standard REST convention for listing a sub-resource (api-convention spec:
"Nested sub-resource" scenario). Offset and page size are query parameters with
defaults `offset=0`, `pageSize=20`. Maximum `pageSize` is capped at 100
(consistent with existing `ListMeetingsRequest` pattern).

Alternatives considered:

- `POST /meetings/{id}/join-requests` (action-as-POST) — rejected; this is a
  pure read with no side effects. `GET` is correct.

### D2 — Authorization: `edit-meeting` authority + host ownership check

`@PreAuthorize("hasAuthority('edit-meeting')")` at the presentation layer, plus
an explicit `NOT_OWNER` check in the application service (same as
`acceptJoinRequests` / `declineJoinRequests`).

Alternative: `view-meeting` — rejected; the pending queue is sensitive (reveals
who is trying to join) and only actionable by the host.

### D3 — Meeting lookup: read-only path via `findDetailById`

The application service loads the meeting via
`meetingRepository.findDetailById(meetingId)` (non-locking, read-only
transaction) to verify existence and ownership before querying Redis. This
avoids acquiring a `SELECT FOR UPDATE` lock that is only needed for writes.

Alternative: `findActiveByIdWithLock` — rejected; locking is unnecessary for a
read-only operation and degrades concurrency.

### D4 — No new `MeetingError` variant

Existence and ownership errors (`MeetingNotFound`, `NotOwner`) are already
defined in `MeetingError`. No new error codes are needed.

## Risks / Trade-offs

- **TTL race**: A `JoinRequest` may expire between the list response and the
  host's accept/decline call. The accept/decline services already handle
  `EXPIRED` status gracefully with `JOIN_REQUEST_EXPIRED` per-item errors. No
  additional mitigation needed.
- **Large queues**: If many participants join simultaneously the offset list may
  be large. The `pageSize=100` cap and Redis `ZRANGEBYSCORE` scan are both O(log
  N + M) — acceptable for typical meeting sizes.

## Migration Plan

No schema or data migrations required. The endpoint is purely additive (no
breaking changes). Deploy as part of the normal release cycle.

## Open Questions

None.
