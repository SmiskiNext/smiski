# complete-meeting Tasks

## 1. Application layer contracts

- [x] 1.1 Add `EndMeetingCommand` record
      `(UUID meetingId, String tenantId, String accountId)` in
      `application/command`, implementing `Command`
- [x] 1.2 Add `EndMeetingResult` record in `application/result` mirroring
      `CancelMeetingResult` (completed meeting snapshot; framework-agnostic, no
      Jackson/Swagger)
- [x] 1.3 Add `EndMeetingUseCase` interface in `application/usecase` extending
      `UseCase<EndMeetingCommand, EndMeetingResult, MeetingError>`
- [x] 1.4 Add `EndedMeetingMapper` in `application/mapper` mapping a completed
      `Meeting` to `EndMeetingResult`

## 2. Application service

- [x] 2.1 Add `EndMeetingApplicationService` (`@Service @Transactional`)
      implementing `EndMeetingUseCase`, injecting `MeetingRepository`,
      `ParticipationLogRepository`, `EventPublisher`, `LiveKitPort`
- [x] 2.2 Load the meeting via `findActiveByIdWithLock`; return
      `MeetingNotFound` when absent
- [x] 2.3 Authorize host: return `NotAuthorized` when the acting account is not
      the host
- [x] 2.4 Call `meeting.complete()`; propagate `InvalidStatusTransition` failure
      unchanged when status is not `RUNNING`
- [x] 2.5 Close every active participation log via `findActiveByMeetingId` +
      `leave(now)` and save each, matching the `room_finished` closure behavior
- [x] 2.6 Save the meeting and `publishEventsOf(meeting)` in the same
      transaction
- [x] 2.7 Invoke `liveKitPort.deleteRoom(roomName)` as best-effort; log on
      failure and do NOT roll back or fail the request ← (verify: transaction
      commits and MeetingCompletedEvent is published even when deleteRoom
      returns failure)

## 3. Presentation layer

- [x] 3.1 Add `EndMeetingResponse` record in `presentation/response` with a
      static `from(EndMeetingResult)` factory
- [x] 3.2 Inject `EndMeetingUseCase` into `MeetingController` (constructor +
      field)
- [x] 3.3 Add `@PostMapping("/meetings/{id}:end")` handler resolving account
      from `AccountContext` and tenant from `TenantContext`, returning
      missing-account `400` consistent with sibling endpoints, mapping `Result`
      via `ResultResponder` to `200 OK`
- [x] 3.4 Add OpenAPI `@Operation`/`@ApiResponses` annotations for `200`, `400`,
      `403`, `404`, `409` matching the cancel endpoint style ← (verify: endpoint
      path is /api/1/meetings/{id}:end and all documented status codes match
      spec scenarios)

## 4. Tests — application (fast suite, mocked ports)

- [x] 4.1 Host ends a RUNNING meeting → COMPLETED, endTime set, active logs
      closed, one `MeetingCompletedEvent` published, result returned
- [x] 4.2 Non-host end request → `NotAuthorized`, meeting unchanged, no event
      published
- [x] 4.3 Unknown/soft-deleted meeting → `MeetingNotFound`, no event published
- [x] 4.4 Ending a SCHEDULED meeting → `InvalidStatusTransition`, no state
      change, no event
- [x] 4.5 Ending an already-COMPLETED meeting → `InvalidStatusTransition`, no
      state change, no event
- [x] 4.6 Ending a CANCELED meeting → `InvalidStatusTransition`, no state
      change, no event
- [x] 4.7 Completion with no active participation logs still succeeds and
      transitions to COMPLETED
- [x] 4.8 `deleteRoom` failure does not roll back completion: status stays
      COMPLETED and event still published ← (verify: best-effort semantics —
      failure is swallowed, not propagated)

## 5. Tests — presentation (integration suite)

- [x] 5.1 `POST /api/1/meetings/{id}:end` as host returns `200 OK` with
      completed snapshot
- [x] 5.2 Non-host returns `403` problem+json with code `NOT_AUTHORIZED`
- [x] 5.3 Missing account header returns `400` problem+json with code
      `VALIDATION_ERROR`
- [x] 5.4 Unknown meeting returns `404` problem+json with code
      `MEETING_NOT_FOUND`
- [x] 5.5 Non-RUNNING meeting returns `409` problem+json with code
      `INVALID_STATUS_TRANSITION` ← (verify: all controller responses match spec
      scenarios and api-convention problem+json shape)

## 6. Verification

- [x] 6.1 Run `./services/gradlew -p services/meet test` (unit + ArchUnit) and
      fix failures
- [x] 6.2 Run `./services/gradlew -p services/meet integrationTest` and fix
      failures
- [x] 6.3 Run `./services/gradlew -p services/meet generateOpenApiDocsFromTests`
      to regenerate `services/meet/openapi.yaml`
- [x] 6.4 Run `./services/gradlew spotlessApply` to format ← (verify: build
      green, openapi.yaml contains the :end path, no ArchUnit/layering
      violations)
