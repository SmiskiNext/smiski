## Why

The Jira issue panel (UC02/UC07) needs to list every meeting linked to a single
Jira issue, addressed by the issue's stable numeric `issueId`. Today the only
listing endpoint is `POST /meetings` (keyset/cursor pagination, filtered by
`issueKey`), which offers no offset-based navigation and keys on the mutable
`issueKey` rather than the indexed `issue_id` column. An offset-paginated,
issue-scoped listing gives the panel a total count and page-number navigation
backed by the existing `idx_meetings_issue(tenant_id, issue_id)` index.

## What Changes

- Add a new endpoint `POST /api/1/issues/{issueId}/meetings` that lists meetings
  linked to the given `issueId` using **offset pagination**.
- Request body carries `offset` (default `0`, min `0`) and `pageSize` (default
  `20`, min `1`, max `50`).
- Response is a bespoke offset page envelope: `data` (array of meeting
  summaries, reusing the existing `MeetingSummary` shape) plus `meta` with
  `total`, `offset`, `pageSize`, and `hasNext`.
- Results are filtered to non-deleted meetings for the issue, scoped to the
  caller's tenant, ordered `createdAt DESC, id DESC`.
- Requires `X-Account-Id` header and `view-meeting` authority; the account is
  used for auth context only, not as a filter.
- NOTE: `POST` on a nested collection here means "list/search", consistent with
  the existing `POST /meetings` list convention in this service — it does not
  create a resource.

## Capabilities

### New Capabilities

- `list-issue-meetings`: Offset-paginated listing of meetings linked to a single
  Jira issue by `issueId`, scoped to the caller's tenant, returning a total
  count for page-number navigation.

### Modified Capabilities

<!-- No existing spec requirements change. POST /meetings (list-tenant-meetings)
     and GET /meetings/{id}/join-requests (list-pending-join-requests) are left
     untouched. -->

## Impact

- **Service**: `meet` only.
- **Presentation**: new handler on `MeetingController`; new request DTO
  (`ListIssueMeetingsRequest`) and response DTO
  (`IssueMeetingListPageResponse`).
- **Application**: new `ListIssueMeetingsQuery`, `ListIssueMeetingsResult`,
  `ListIssueMeetingsUseCase`, `ListIssueMeetingsApplicationService`.
- **Domain**: new port method `MeetingRepository#findSummariesByIssueId`
  returning an offset page of `MeetingSummary` plus total.
- **Infrastructure**: `MeetingRepositoryAdapter` gains an offset+limit query via
  `JpaSpecificationExecutor` (reuses the `issue_id` index and `toSummary`).
- **API spec**: regenerated `services/meet/openapi.yaml`
  (`generateOpenApiDocsFromTests` + root `pnpm run openapi`).
- **Out of scope**: no change to `POST /meetings`; no frontend/SDK changes (the
  app's `listIssueMeetings` keeps using the existing `issueKey` path); no change
  to shared paging classes.
