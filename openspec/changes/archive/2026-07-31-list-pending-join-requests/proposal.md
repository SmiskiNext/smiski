## Why

When a meeting uses `MANUAL_APPROVAL` admission policy, the host needs to see
who is waiting in the lobby before deciding to approve or deny. Currently the
`accept` and `decline` endpoints exist, but there is no endpoint to retrieve the
pending queue — the host has no way to discover pending request IDs without
out-of-band signaling.

## What Changes

- Add `GET /meetings/{id}/join-requests` endpoint that returns a paginated
  (offset-based) list of `PENDING` join requests for a given meeting.
- Only the meeting host may call this endpoint; any other authenticated tenant
  member receives `403 NOT_OWNER`.

## Capabilities

### New Capabilities

- `list-pending-join-requests`: Read-only query endpoint exposing the pending
  join-request queue for a meeting to its host, with offset pagination.

### Modified Capabilities

<!-- No existing spec-level requirements are changing. -->

## Impact

- **New files**: `application/query/ListPendingJoinRequestsQuery`,
  `application/usecase/ListPendingJoinRequestsUseCase`,
  `application/result/ListPendingJoinRequestsResult`,
  `application/service/ListPendingJoinRequestsApplicationService`,
  `presentation/response/ListPendingJoinRequestsResponse`.
- **Modified files**: `presentation/MeetingController` (inject new use case, add
  GET handler), `openapi.yaml` (new operation).
- **No schema or Redis key changes**: reuses
  `JoinRequestRepository.findPendingSummariesByMeetingId()` and existing
  `JoinRequestSummary` projection.
- **No domain model changes**: read-only query path only.
