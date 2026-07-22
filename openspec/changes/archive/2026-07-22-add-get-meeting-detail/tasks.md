# Implementation Tasks

## 1. Infrastructure — participation log read path

- [x] 1.1 Add a query to `ParticipationLogJpaRepository` returning all
      participation-log rows for a `meetingId` (tenant-filtered by `@TenantId`)
- [x] 1.2 Implement
      `ParticipationLogRepositoryAdapter.findDistinctParticipantSummariesByMeetingId`:
      group rows by `accountId`, computing earliest `joinedAt`, `leftAt` = null
      if any open session else latest `leftAt`, and `displayName`/`role`/`id`
      from the most recent session; replace the `UnsupportedOperationException`
      stub ← (verify: collapsing rule matches design.md — one row per account,
      earliest join, null-if-open-else-latest leave; tenant isolation preserved)

## 2. Application — use case

- [x] 2.1 Add `GetMeetingQuery` record (`meetingId`, `tenantId`, `accountId`)
      implementing `Query`
- [x] 2.2 Add `GetMeetingResult` record (framework-agnostic): meeting fields +
      `List<Invitee>` + `List<Participant>` nested records
- [x] 2.3 Add mapper(s) from `Meeting` + `InviteeSummary` + `ParticipantSummary`
      to `GetMeetingResult` (follow `MeetingSummaryMapper` style)
- [x] 2.4 Add `GetMeetingUseCase` interface extending
      `UseCase<GetMeetingQuery, GetMeetingResult, MeetingError>`
- [x] 2.5 Add `GetMeetingApplicationService` (`@Service`,
      `@Transactional(readOnly=true)`): load via `MeetingRepository.findById`;
      return `MeetingError.MeetingNotFound` when absent or soft-deleted;
      otherwise fetch invitees and distinct participants and map to result ←
      (verify: absent, soft-deleted, and other-tenant meetings all yield
      MeetingNotFound and are indistinguishable)

## 3. Presentation — endpoint

- [x] 3.1 Add `GetMeetingResponse` DTO (nested `meeting` snapshot without tenant
      id, `invitees[]`, `participants[]`) with a `from(GetMeetingResult)`
      factory and `@Schema` annotations matching sibling responses
- [x] 3.2 Add `@GetMapping("/meetings/{id}")` handler to `MeetingController`:
      inject `GetMeetingUseCase`, guard missing `X-Account-Id` with `400`, build
      the query from path id + tenant + account, map result via
      `GetMeetingResponse::from`, and document with `@Operation`/`@ApiResponses`
      (200, 400 missingAccount, 404 notFound) ← (verify: route is GET
      /api/1/meetings/{id}, 200 payload shape and 404 problem+json match the
      spec scenarios)

## 4. Tests — application (fast, no Spring)

- [x] 4.1 Test: existing tenant meeting returns meeting + invitees +
      participants
- [x] 4.2 Test: absent meeting → `MeetingNotFound`
- [x] 4.3 Test: soft-deleted meeting → `MeetingNotFound`
- [x] 4.4 Test: empty invitee list and empty participant list still succeed

## 5. Tests — infrastructure (integrationTest, Testcontainers)

- [x] 5.1 Adapter test: a rejoining account collapses to one participant with
      earliest `joinedAt`
- [x] 5.2 Adapter test: an account that left is listed with non-null `leftAt`;
      an account with an open session has `leftAt == null`
- [x] 5.3 Adapter test: participation logs of another tenant are not returned

## 6. Tests — presentation (integrationTest)

- [x] 6.1 Controller test: `GET /meetings/{id}` returns `200` with the meeting,
      invitees, and participants for a tenant member who is not the host
- [x] 6.2 Controller test: missing `X-Account-Id` returns `400` problem+json
- [x] 6.3 Controller test: unknown or soft-deleted id returns `404`
      `MEETING_NOT_FOUND` problem+json ← (verify: all get-meeting-detail spec
      scenarios have a corresponding test)

## 7. Contract & formatting

- [x] 7.1 Regenerate `services/meet/openapi.yaml` via
      `generateOpenApiDocsFromTests` and confirm the new `GET /meetings/{id}`
      path
- [x] 7.2 Run `./services/gradlew -p services/meet test integrationTest` and
      `./services/gradlew spotlessApply`; ensure ArchUnit and all tests pass ←
      (verify: build green, OpenAPI includes the endpoint, no ArchUnit
      violations)
