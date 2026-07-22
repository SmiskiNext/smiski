## 1. Domain layer

- [x] 1.1 Add `MeetingDeletedEvent` (PublishableEvent) in `domain/event/`
      carrying a full aggregate snapshot mirroring `MeetingStartedEvent`
      (`eventId, tenantId, meetingId, hostId, shortCode, type, status, title, description, issueId, issueKey, projectKey, startTime, endTime, settings, zoneId, organizerEmail, organizerDisplayName, calendarUid, calendarSequence, createdAt`)
      plus `deletedBy, deletedAt`; topic `meet.meeting.deleted`; eventType
      `io.github.smiskinext.meet.meeting.deleted.v1`;
      `aggregateType() = "meeting"`; `occurredAt() = deletedAt`. Wire event
      reuses shared `MeetingSnapshot` proto.
- [x] 1.2 Add `CannotDeleteRunningMeeting(UUID meetingId)` variant to
      `MeetingError` and `CANNOT_DELETE_RUNNING_MEETING(ErrorCategory.CONFLICT)`
      to `MeetingErrorCode`
- [x] 1.3 Add `Meeting.delete(AccountId deletedBy)` returning
      `Result<Void, MeetingError>`: reject when not host (`NotAuthorized`),
      reject when status is `RUNNING` (`CannotDeleteRunningMeeting`); on success
      set `deletedAt = now`, `deletedBy`, update `updatedAt`, leave `purgeAfter`
      null, and register `MeetingDeletedEvent` ← (verify: authorization +
      running-meeting guards match spec, event registered only on success)

## 2. Domain port + infrastructure

- [x] 2.1 Add `findActiveByIdWithLock(UUID id)` to `MeetingRepository`
      (pessimistic write lock, excludes soft-deleted rows)
- [x] 2.2 Add matching locked query to `MeetingJpaRepository` filtering
      `deletedAt IS NULL`
- [x] 2.3 Implement `findActiveByIdWithLock` in `MeetingRepositoryAdapter`
      mapping via `MeetingPersistenceMapper` ← (verify: already-deleted and
      unknown meetings both return empty)

## 3. Application layer — single delete

- [x] 3.1 Add
      `DeleteMeetingCommand(UUID meetingId, String tenantId, String accountId)`
      and `DeleteMeetingResult` (minimal) records
- [x] 3.2 Add `DeleteMeetingUseCase` interface extending
      `UseCase<DeleteMeetingCommand, DeleteMeetingResult, MeetingError>`
- [x] 3.3 Add `@Transactional DeleteMeetingApplicationService`: load via
      `findActiveByIdWithLock` (→ `MeetingNotFound` if absent), call
      `meeting.delete(...)`, on success `save` + `publishEventsOf` ← (verify:
      404 for missing/already-deleted, no event on any rejection)

## 4. Application layer — batch delete

- [x] 4.1 Add
      `BatchDeleteMeetingsCommand(List<UUID> meetingIds, String tenantId, String accountId)`
      and `BatchDeleteMeetingsResult` (minimal) records
- [x] 4.2 Add `BatchDeleteMeetingsUseCase` interface
- [x] 4.3 Add `@Transactional BatchDeleteMeetingsApplicationService`:
      validate-all-then-mutate — load+lock each id, on first failure return that
      `MeetingError` (transaction rolls back), otherwise soft-delete all +
      publish one event per meeting ← (verify: any single failing id deletes
      nothing and publishes nothing; atomic rollback)

## 5. Presentation layer

- [x] 5.1 Add `BatchDeleteMeetingsRequest` DTO:
      `@NotEmpty List<UUID> meetingIds` with `toCommand(accountId, tenantId)`
- [x] 5.2 Add `@DeleteMapping("/meetings/{id}")` handler to `MeetingController`:
      resolve account header (400 if missing) + tenant, call use case, return
      `responder.noContent(result)`, with `@Operation`/`@ApiResponses` (204,
      400, 403, 404, 409)
- [x] 5.3 Add `@PostMapping("/meetings:batchDelete")` handler: resolve account
      header + tenant, call batch use case, return
      `responder.noContent(result)`, with `@Operation`/`@ApiResponses` (204,
      400, 403, 404, 409) ← (verify: routes resolve to `/api/1/meetings/{id}`
      and `/api/1/meetings:batchDelete`, 204 on success)

## 6. Localization

- [x] 6.1 Add `error.cannot-delete-running-meeting.title/.detail` to
      `meet.properties` (English)
- [x] 6.2 Add `error.cannot-delete-running-meeting.title/.detail` to
      `meet_vi.properties` (Vietnamese)

## 7. Tests — domain (fast)

- [x] 7.1 Host deletes an eligible (`SCHEDULED`/`COMPLETED`/`CANCELED`) meeting
      → success, `deletedAt`/`deletedBy` set, `MeetingDeletedEvent` registered
- [x] 7.2 Non-host delete → `NotAuthorized`, no event registered
- [x] 7.3 Delete a `RUNNING` meeting → `CannotDeleteRunningMeeting`, no event
      registered

## 8. Tests — application (fast, mocked ports)

- [x] 8.1 Single delete: missing/already-deleted meeting → `MeetingNotFound`, no
      save, no publish
- [x] 8.2 Single delete success → saves meeting and publishes exactly one event
- [x] 8.3 Batch delete all-eligible → all soft-deleted, one event per meeting
- [x] 8.4 Batch delete with one running / not-found / non-host id → returns the
      error, no save, no publish (atomic) ← (verify: rollback leaves every
      meeting undeleted)

## 9. Tests — integration (controller, Testcontainers)

- [x] 9.1 `DELETE /api/1/meetings/{id}` by host → `204`; meeting absent from
      list afterwards
- [x] 9.2 `DELETE` by non-host → `403` problem+json; missing account header →
      `400`
- [x] 9.3 `DELETE` unknown or already-deleted id → `404` problem+json
- [x] 9.4 `DELETE` running meeting → `409` problem+json with
      `CANNOT_DELETE_RUNNING_MEETING`
- [x] 9.5 `POST /api/1/meetings:batchDelete` all-eligible → `204`; empty list →
      `400`
- [x] 9.6 `POST :batchDelete` containing one ineligible id → error response and
      no meeting deleted ← (verify: atomic behavior end-to-end)

## 10. API spec + formatting

- [x] 10.1 Run
      `./services/gradlew -p services/meet generateOpenApiDocsFromTests` and
      confirm the two endpoints appear in `services/meet/openapi.yaml`
- [x] 10.2 Run `./services/gradlew spotlessApply`, then
      `./services/gradlew -p services/meet test` and `integrationTest` green ←
      (verify: full build passes, ArchUnit naming satisfied)
