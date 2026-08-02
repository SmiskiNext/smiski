## Why

The tenant meeting listing (`POST /api/1/meetings`) can filter by an exact
`issueKey` but not by project, so the project-page dashboard has no backend to
call (`listProjectMeetings` in the Forge app still throws "Not implemented").
The meeting summary also omits `issueId` and `projectKey` even though both are
stored, forcing clients to derive the project from the issue-key prefix.

## What Changes

- Add an optional `projectKey` filter to the `POST /api/1/meetings` request
  body. When present, only meetings whose linked Jira `projectKey` equals it are
  returned; when absent or blank, project scoping is not applied. It composes
  (AND) with the existing `issueKey`, `creatorId`, `statuses`, and `search`
  filters and uses the same exact, case-sensitive match as `issueKey`.
- Expand each meeting summary in the response to carry `issueId` and
  `projectKey` alongside the existing `issueKey`, so clients receive the full
  Jira issue link without inference.
- Regenerate the `meet` service OpenAPI spec to reflect the new request field
  and summary fields.

## Capabilities

### New Capabilities

<!-- None. This extends an existing capability. -->

### Modified Capabilities

- `list-tenant-meetings`: add a `projectKey` request filter (new requirement),
  include `projectKey` in the accepted request body, and add `issueId` and
  `projectKey` to the meeting summary shape.

## Impact

- Service `meet` (Spring Boot / Java 25):
    - `presentation/request/ListMeetingsRequest.java` — new `projectKey` field +
      `toQuery`
    - `application/query/ListMeetingsQuery.java` — new `projectKey` param
    - `domain/projection/MeetingSearchCriteria.java` — new `projectKey` field
    - `application/service/ListMeetingsApplicationService.java` — pass
      `projectKey` to criteria
    - `infrastructure/persistence/MeetingRepositoryAdapter.java` —
      `projectKeyIs` spec + `issueId`/`projectKey` in `toSummary`
    - `domain/projection/MeetingSummary.java` — add `issueId`, `projectKey`
    - `application/result/ListMeetingsResult.Item` — add `issueId`, `projectKey`
    - `application/mapper/MeetingSummaryMapper.java` — map new fields
    - `presentation/response/MeetingSummaryResponse.java` — add `issueId`,
      `projectKey` + `from`
    - `presentation/MeetingController.java` — fallback request arity,
      `@Operation`/example update
- Generated artifact: `services/meet/openapi.yaml`
- Out of scope: Forge app (`app/`) wiring, SDK regeneration, per-project RBAC on
  `view-meeting`, extending `search` to cover `projectKey`.
