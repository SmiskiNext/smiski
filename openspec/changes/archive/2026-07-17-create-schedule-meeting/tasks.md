# Implementation Tasks

## 1. Domain

- [x] 1.1 Add `MeetingErrorCode.MEETING_START_IN_PAST` with category
      `VALIDATION`
- [x] 1.2 Add `MeetingError.StartTimeInPast` record (with the offending start
      time as message arg) referencing the new code
- [x] 1.3 Add a `CLOCK_SKEW_TOLERANCE` constant to `Meeting` (e.g. 2 minutes)
- [x] 1.4 Change `Meeting.schedule()` to return `Result<Meeting, MeetingError>`,
      validating `startTime >= now - CLOCK_SKEW_TOLERANCE` and returning
      `StartTimeInPast` otherwise
- [x] 1.5 Fix `Meeting.schedule()` so the `MeetingCreatedEvent` carries
      `timeRange.end()` instead of `null` for `endTime` ← (verify: created event
      snapshot has both start and end; return type change breaks no existing
      caller)

## 2. Application

- [x] 2.1 Create `ScheduleMeetingCommand` (tenantId, title, description,
      issueLink, settings, hostAccountId, timeRange{start,end}, invitees) — no
      host display/device/avatar fields
- [x] 2.2 Create `ScheduleMeetingResult` (meeting snapshot fields + startTime +
      endTime; no LiveKit fields)
- [x] 2.3 Create `ScheduleMeetingUseCase` interface extending shared
      `UseCase<Command, Result, MeetingError>`
- [x] 2.4 Create `ScheduleMeetingApplicationService` implementing the use case:
      allocate short code, call `Meeting.schedule()`, propagate
      `StartTimeInPast` failure, build invitees + invite tokens when present,
      persist meeting (+ invitees) in one transaction, publish events; inject no
      `LiveKitPort` and never call `meeting.start()` ← (verify: SCHEDULED status
      preserved, no token issued, invitee/event logic matches instant flow)

## 3. Presentation

- [x] 3.1 Create `ScheduleMeetingRequest` (title, description, issueLink,
      settings, timeRange{startTime,endTime}, optional invitees) with Jakarta
      validation and `toCommand(accountId, tenantId)`; no host object
- [x] 3.2 Create `ScheduleMeetingResponse` with a static `from(result)` factory;
      snapshot + startTime + endTime, no `livekit` object
- [x] 3.3 Add `POST /meetings:schedule` to `MeetingController`: resolve host
      from `X-Account-Id` (400 if missing), map result via
      `ResultResponder.created` with `Location` header, OpenAPI
      `@Operation`/`@ApiResponses` annotations ← (verify: 201 + Location +
      token-free snapshot; missing header → 400; StartTimeInPast → 400
      MEETING_START_IN_PAST)

## 4. Resources

- [x] 4.1 Add `error.meeting-start-in-past.title` and `.detail` to
      `meet.properties`
- [x] 4.2 Add `error.meeting-start-in-past.title` and `.detail` to
      `meet_vi.properties`

## 5. Tests

- [x] 5.1 Domain unit test: future start time is accepted and time range
      persisted (Scenario: Future start time is accepted)
- [x] 5.2 Domain unit test: start within clock-skew tolerance is accepted
      (Scenario: Start time within clock-skew tolerance is accepted)
- [x] 5.3 Domain unit test: past start returns `StartTimeInPast` (Scenario:
      Start time in the past is rejected)
- [x] 5.4 Domain regression test: `Meeting.schedule()` created event carries
      both start and end time (bug-fix regression)
- [x] 5.5 Application service test: successful scheduled creation yields
      SCHEDULED meeting, no LiveKit interaction, events published (Scenarios:
      Meeting is created scheduled and not started; Created event carries start
      and end time)
- [x] 5.6 Application service test: invitees persisted with hashed tokens and
      invitations event enqueued; empty invitees produce none (Scenarios:
      Invitees persisted with hashed tokens; No invitees produces no invitations
      event)
- [x] 5.7 Application service test: short-code exhaustion fails without
      persisting (Scenario: Short code uniqueness is enforced with retry)
- [x] 5.8 Integration test: valid request returns 201 with token-free snapshot
      (type/status SCHEDULED, startTime/endTime present, no livekit, no
      tenantId) (Scenario: Successful scheduled creation returns snapshot
      without token)
- [x] 5.9 Integration test: missing `X-Account-Id` returns 400 and persists
      nothing (Scenario: Missing host header is rejected)
- [x] 5.10 Integration test: missing timeRange / settings / description /
      issueLink and maxParticipants > 100 each return 400 and persist nothing
      (Scenarios: missing/invalid field validation errors)
- [x] 5.11 Integration test: past start time returns 400 `MEETING_START_IN_PAST`
      and persists nothing (Scenario: Start time in the past is rejected)
- [x] 5.12 Integration test: invalid invitee email / missing invitee accountId
      or displayName return 400 and persist nothing (Scenarios: invitee
      validation errors) ← (verify: all endpoint scenarios covered, nothing
      persisted on failure)

## 6. Verification

- [x] 6.1 Run `./services/gradlew -p services/ meet test`
- [x] 6.2 Run `./services/gradlew -p services/ meet integrationTest`
- [x] 6.3 Run
      `./services/gradlew -p services/ meet generateOpenApiDocsFromTests` and
      confirm `services/meet/openapi.yaml` includes `/meetings:schedule`
- [x] 6.4 Run `./services/gradlew spotlessApply` ← (verify: full build green,
      OpenAPI regenerated, formatting clean)
