## 1. Application Layer

- [x] 1.1 Create `application/query/ListPendingJoinRequestsQuery.java` — record
      with `meetingId`, `tenantId`, `accountId`, `offset`, `pageSize`
      implementing `Query`
- [x] 1.2 Create `application/usecase/ListPendingJoinRequestsUseCase.java` —
      interface extending
      `UseCase<ListPendingJoinRequestsQuery, ListPendingJoinRequestsResult, MeetingError>`
- [x] 1.3 Create `application/result/ListPendingJoinRequestsResult.java` —
      record wrapping `OffsetPageResponse<JoinRequestSummary>`
- [x] 1.4 Create
      `application/service/ListPendingJoinRequestsApplicationService.java` —
      `@Service @Transactional(readOnly=true)`, load meeting via
      `findDetailById`, verify caller is host, delegate to
      `joinRequestRepository.findPendingSummariesByMeetingId()`

## 2. Presentation Layer

- [x] 2.1 Create `presentation/response/ListPendingJoinRequestsResponse.java` —
      record with `List<Item> results` and `Meta meta`; `Item` contains
      `requestId`, `accountId`, `displayName`, `status`, `requestedAt`,
      `expiresAt`; static `from(ListPendingJoinRequestsResult)` factory
- [x] 2.2 Add `GET /meetings/{id}/join-requests` handler to `MeetingController`
      — inject `ListPendingJoinRequestsUseCase`, `@RequestParam` `offset`
      (default `0`) and `pageSize` (default `20`, max `100`),
      `@PreAuthorize("hasAuthority('edit-meeting')")`, validate `pageSize` range
      `[1,100]` ← (verify: 400 on pageSize=0 and pageSize=101, 400 on missing
      X-Account-Id)
- [x] 2.3 Update `openapi.yaml` — add `listPendingJoinRequests` operation under
      `GET /api/{version}/meetings/{id}/join-requests` with request params,
      200/400/403/404 responses

## 3. Unit Tests

- [x] 3.1 Test: host with 3 pending requests → `200 OK` with 3 items ordered by
      `requestedAt` asc (covers spec scenario: "Host lists pending requests —
      meeting has waiters")
- [x] 3.2 Test: empty queue → `200 OK` with empty `results` (covers spec
      scenario: "empty queue")
- [x] 3.3 Test: second page with offset → correct slice returned (covers spec
      scenario: "Offset pagination — second page")
- [x] 3.4 Test: non-host caller → `Result.failure(NotOwner)` (covers spec
      scenario: "Non-host caller is rejected")
- [x] 3.5 Test: meeting not found → `Result.failure(MeetingNotFound)` (covers
      spec scenario: "Meeting not found") ← (verify: all 5 unit test scenarios
      pass in `test` source set without Spring context)

## 4. Integration Tests

- [x] 4.1 Controller integration test: happy path — host retrieves pending list,
      verifies JSON shape and `meta` fields
- [x] 4.2 Controller integration test: non-host → `403` Problem Details with
      `code: NOT_OWNER`
- [x] 4.3 Controller integration test: unknown meeting id → `404` Problem
      Details with `code: MEETING_NOT_FOUND`
- [x] 4.4 Controller integration test: missing `X-Account-Id` header → `400`
      Problem Details with `code: VALIDATION_ERROR`
- [x] 4.5 Controller integration test: `pageSize=0` and `pageSize=101` → `400`
      Problem Details ← (verify: all 5 integration scenarios pass;
      `./services/gradlew -p services/meet integrationTest` is green)

## 5. Verification

- [x] 5.1 Run `./services/gradlew -p services/meet test` — green
- [x] 5.2 Run `./services/gradlew -p services/meet integrationTest` — green
- [x] 5.3 Run `./services/gradlew spotlessApply` — no formatting violations
- [x] 5.4 Run `./services/gradlew -p services/meet generateOpenApiDocsFromTests`
      — `openapi.yaml` updated and includes `listPendingJoinRequests` operation
      ← (verify: generated spec matches design.md endpoint shape; ArchUnit
      `test` source set passes)
