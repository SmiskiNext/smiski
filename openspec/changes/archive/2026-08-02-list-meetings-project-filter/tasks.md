## 1. Filter path: accept `projectKey` in the request

- [x] 1.1 Add nullable `projectKey` field (with `@Schema`) to
      `ListMeetingsRequest` and pass it into `toQuery(...)`
- [x] 1.2 Add `projectKey` parameter to `ListMeetingsQuery`
- [x] 1.3 Add `projectKey` field to `MeetingSearchCriteria`
- [x] 1.4 Pass `query.projectKey()` into the criteria in
      `ListMeetingsApplicationService`
- [x] 1.5 Add a `projectKeyIs` specification and apply it (exact match, skipped
      when null/blank) in `MeetingRepositoryAdapter.searchSpecification` ←
      (verify: filter ANDs with existing filters, blank is skipped, matches
      issueKey pattern)

## 2. Response path: return `issueId`, `issueKey`, `projectKey`

- [x] 2.1 Add `issueId` and `projectKey` fields to `MeetingSummary` projection
- [x] 2.2 Map `entity.getIssueId()` and `entity.getProjectKey()` in
      `MeetingRepositoryAdapter.toSummary`
- [x] 2.3 Add `issueId` and `projectKey` to `ListMeetingsResult.Item`
- [x] 2.4 Map the two new fields in `MeetingSummaryMapper`
- [x] 2.5 Add `issueId` and `projectKey` to `MeetingSummaryResponse` and update
      its `from(...)` factory ← (verify: summary exposes issueId + issueKey +
      projectKey, still omits tenant id)

## 3. Controller and OpenAPI

- [x] 3.1 Update the empty-body fallback `new ListMeetingsRequest(...)` for the
      added `projectKey` field in `MeetingController`
- [x] 3.2 Update the `@Operation` description and the 200 example to include the
      `projectKey` filter and the `issueId`/`projectKey` summary fields
- [x] 3.3 Regenerate `services/meet/openapi.yaml` via
      `generateOpenApiDocsFromTests`

## 4. Spec-derived tests

- [x] 4.1 Unit test in `ListMeetingsApplicationServiceTest`: a request carrying
      `projectKey` propagates it into the `MeetingSearchCriteria` passed to the
      repository
- [x] 4.2 Integration test in `MeetingSearchRepositoryAdapterIntegrationTest`:
      `projectKey` filter returns only meetings in that project and excludes
      others (extend the `criteria(...)` helper for the new field)
- [x] 4.3 Integration test: `projectKey` combined with `issueKey` ANDs both
      filters
- [x] 4.4 Integration test: returned `MeetingSummary` carries `issueId`,
      `issueKey`, and `projectKey` ← (verify: values match inserted rows, no
      tenant id present)

## 5. Verification

- [x] 5.1 Run `./services/gradlew -p services/meet test`
- [x] 5.2 Run `./services/gradlew -p services/meet integrationTest`
- [x] 5.3 Run `./services/gradlew spotlessApply` and confirm the build is clean
      ← (verify: all checks green, openapi.yaml regenerated and committed)
