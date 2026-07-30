## 1. Protobuf Definition

- [x] 1.1 Create
      `services/proto/src/main/proto/io/github/smiskinext/event/meet/v1/meeting_canceled.proto`
      with a `MeetingCanceled` message (fields: meeting_id, tenant_id, host_id,
      cancel_reason, meeting_title, meeting_short_code, start_time, repeated
      InviteeInfo invitees, canceled_at) and nested `InviteeInfo` message
      (fields: account_id, email, display_name, status, invited_at) ← (verify:
      proto compiles without errors, generated Java class `MeetingCanceled` is
      available in `io.github.smiskinext.event.meet.v1` package)

## 2. Infrastructure — Event Mapper

- [x] 2.1 Create `MeetingCanceledEventProtoMapper` (`@Component`) in
      `services/meet/.../infrastructure/messaging/` implementing
      `OutboxEventProtoMapper<MeetingCanceledEvent>` with `eventType()`,
      `dataSchema()` returning
      `"io.github.smiskinext.event.meet.v1.MeetingCanceled"`, and `toProto()`
      mapping all fields including the invitee list ← (verify: publishing a
      `MeetingCanceledEvent` via the outbox does not throw
      `IllegalStateException`; mapper is auto-discovered by
      `OutboxEventProtoMapperRegistry`)

## 3. Application Layer — Manual Cancel

- [x] 3.1 Create `CancelMeetingCommand` record in `application/command/` with
      fields `meetingId (UUID)`, `tenantId (String)`, `accountId (String)`
      implementing `Command`
- [x] 3.2 Create `CancelMeetingResult` record in `application/result/` with full
      meeting snapshot fields: `meetingId`, `hostId`, `shortCode`, `type`,
      `status`, `cancelReason`, `title`, `description`, `issueLink`, `settings`,
      `startTime`, `endTime`, `zoneId`, `organizerEmail`,
      `organizerDisplayName`, `calendarUid`, `calendarSequence`, `createdAt`
- [x] 3.3 Create `CancelMeetingUseCase` interface in `application/usecase/`
      extending
      `UseCase<CancelMeetingCommand, CancelMeetingResult, MeetingError>`
- [x] 3.4 Create `CanceledMeetingMapper` static utility in `application/mapper/`
      mapping a canceled `Meeting` aggregate to `CancelMeetingResult`
- [x] 3.5 Create `CancelMeetingApplicationService` (`@Service`,
      `@Transactional`) in `application/service/` implementing
      `CancelMeetingUseCase`: (a) load with `findActiveByIdWithLock`, (b) check
      host authorization returning `NOT_AUTHORIZED` on mismatch, (c) load active
      invitees via `MeetingInviteeRepository.findByMeetingId()` filtering out
      removed ones, (d) map to `MeetingCanceledEvent.InviteeInfo` list, (e) call
      `meeting.cancel(HOST_CANCELED, title, shortCode, startTime, invitees)`,
      (f) save, (g) `eventPublisher.publishEventsOf(meeting)`, (h) return
      `CanceledMeetingMapper.toResult(meeting)` ← (verify: service returns
      correct result on success; authorization check fires before any mutation;
      no save or publish on failure)

## 4. Presentation Layer — Manual Cancel Endpoint

- [x] 4.1 Create `CancelMeetingResponse` record in `presentation/response/` with
      a nested `Meeting` snapshot record and a static
      `from(CancelMeetingResult)` factory; include `@Schema` annotations
      consistent with other response DTOs; the snapshot SHALL include
      `cancelReason` field
- [x] 4.2 Add `CancelMeetingUseCase` field and constructor parameter to
      `MeetingController`; inject it
- [x] 4.3 Add `POST /meetings/{id}:cancel` handler method to `MeetingController`
      with full `@Operation` + `@ApiResponses` OpenAPI annotations covering 200
      (canceled snapshot), 400 (missing account), 403 (not authorized), 404 (not
      found), 409 (invalid status transition), 500; handler reads account from
      `AccountContext`, reads tenant from `TenantContext`, calls
      `cancelMeetingUseCase.execute(command)`, returns
      `responder.ok(result.map(CancelMeetingResponse::from))` ← (verify:
      endpoint appears in generated `openapi.yaml`; 200 response schema includes
      `cancelReason`; `pnpm run openapi` passes lint)

## 5. No-Show Job — Repository Query

- [x] 5.1 Add
      `findScheduledExpiredIdsAcrossTenants(int batchSize, Instant cutoff)`
      native SQL query to `MeetingJpaRepository` returning `List<Object[]>` (or
      a projection) with columns `(id, tenant_id)` where
      `status = 'SCHEDULED' AND end_time < :cutoff AND deleted_at IS NULL` using
      `LIMIT :batchSize FOR UPDATE SKIP LOCKED`
- [x] 5.2 Add `findScheduledExpiredAcrossTenants(int batchSize, Instant cutoff)`
      method to `MeetingRepository` port returning a suitable cross-tenant
      result type (list of id+tenantId pairs)
- [x] 5.3 Implement the port method in `MeetingRepositoryAdapter` ← (verify:
      native query executes without tenant filter; only SCHEDULED meetings with
      past end_time are returned)

## 6. No-Show Job — Service and Scheduler

- [x] 6.1 Create `NoShowMeetingCanceler` (`@Service`) in `application/service/`
      with a `@Transactional`
      `cancelExpiredMeeting(UUID meetingId, String tenantId)` method that: sets
      `TenantContext.setCurrentTenant(tenantId)`, loads with
      `findActiveByIdWithLock`, loads active invitees, calls
      `meeting.cancel(NO_SHOW, …)`, saves, publishes events, clears context in a
      `finally` block; and a non-transactional `cancelAllExpired(int batchSize)`
      method that queries `findScheduledExpiredAcrossTenants` and calls
      `cancelExpiredMeeting` per row
- [x] 6.2 Create `NoShowMeetingCancelerTrigger` (`@Component`) in
      `infrastructure/config/` or a dedicated `infrastructure/scheduler/`
      package with a
      `@Scheduled(fixedDelayString = "${smiski.meet.no-show-canceler.fixed-delay:PT5M}")`
      method delegating to `NoShowMeetingCanceler.cancelAllExpired(batchSize)`
- [x] 6.3 Add `smiski.meet.no-show-canceler.fixed-delay: PT5M` and
      `smiski.meet.no-show-canceler.batch-size: 100` to
      `src/main/resources/application.yaml` ← (verify: job runs on startup after
      delay; tenant context is cleared after each meeting regardless of failure;
      meetings in future are not touched)

## 7. i18n — No New Keys Required

- [x] 7.1 Confirm that existing keys `error.invalid-status-transition.*` and
      `error.not-authorized.*` in `meet.properties` and `meet_vi.properties`
      cover all cancellation error paths; add no new keys ← (verify: 409 and 403
      responses from the cancel endpoint return localized messages without
      `null` or missing-key placeholders)

## 8. Domain Unit Tests

- [x] 8.1 Create `MeetingCancelTest` in `src/test/.../domain/` covering: (a)
      SCHEDULED → CANCELED transitions with HOST_CANCELED and NO_SHOW reasons
      register exactly one `MeetingCanceledEvent` with correct fields, (b)
      invitee list propagated correctly to event, (c) cancel on RUNNING meeting
      returns `InvalidStatusTransition` failure with no event, (d) cancel on
      COMPLETED meeting returns `InvalidStatusTransition` failure with no event,
      (e) cancel on already-CANCELED meeting returns `InvalidStatusTransition`
      failure with no event ← (verify: all scenarios match spec; no domain test
      touches Spring context)

## 9. Application Unit Tests

- [x] 9.1 Create `CancelMeetingApplicationServiceTest` in
      `src/test/.../application/` covering: (a) meeting not found returns
      `MeetingNotFound`, no save or publish; (b) non-host returns
      `NOT_AUTHORIZED`, no save or publish; (c) RUNNING meeting returns
      `InvalidStatusTransition`, no save or publish; (d) successful host cancel
      saves meeting, publishes exactly one event, returns correct snapshot with
      HOST_CANCELED reason and matching invitees ← (verify: mocked invitee list
      is correctly passed to event; publisher is verified with
      `verify(publisher).publishEventsOf(meeting)`)

## 10. No-Show Job Unit Tests

- [x] 10.1 Create `NoShowMeetingCancelerTest` in `src/test/.../application/`
      covering: (a) expired SCHEDULED meeting is canceled with NO_SHOW reason
      and event published; (b) meeting that has already transitioned
      (RUNNING/COMPLETED/CANCELED) is skipped by the domain guard — no save or
      publish; (c) `TenantContext` is set before processing and cleared after
      each meeting even when an exception is thrown ← (verify: job test uses
      mocked repository and publisher; no Spring context; tenant context
      thread-local is verified)

## 11. Presentation Integration Tests

- [x] 11.1 Create `CancelMeetingControllerIntegrationTest` in
      `src/integrationTest/.../presentation/` covering: (a) 200 OK with canceled
      meeting snapshot (including `cancelReason`) for a valid host cancel
      request; (b) 403 with `NOT_AUTHORIZED` when caller is not the host; (c)
      404 with `MEETING_NOT_FOUND` for an unknown ID; (d) 409 with
      `INVALID_STATUS_TRANSITION` when meeting is not SCHEDULED; (e) 400 with
      `VALIDATION_ERROR` when account header is missing ← (verify: response body
      matches OpenAPI schema; `cancelReason` field is `HOST_CANCELED` in 200
      response; problem+json content-type on all error responses)

## 12. Build Verification

- [x] 12.1 Run `./services/gradlew bufFormatApply` and confirm proto formats
      cleanly
- [x] 12.2 Run `./services/gradlew -p services/meet test` and confirm all
      domain + application + ArchUnit tests pass
- [x] 12.3 Run `./services/gradlew -p services/meet integrationTest` and confirm
      controller integration tests and OpenAPI generation pass
- [x] 12.4 Run `pnpm run openapi` from repo root and confirm generated
      `openapi.yaml` includes the new `:cancel` endpoint with correct schemas
      and no lint errors
- [x] 12.5 Run `./services/gradlew spotlessApply` and confirm no formatting
      diffs remain ← (verify: all checks pass cleanly; `openapi.yaml` diff shows
      the new endpoint; no `IllegalStateException` for missing proto mapper)
