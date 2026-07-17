## 1. Shared account-identity filter

- [x] 1.1 Add `AccountContext` (virtual-thread-safe ThreadLocal, get/set/clear)
      in `services/shared/.../infrastructure/identity`
- [x] 1.2 Add `AccountProperties` (configurable header, default `X-Account-Id`)
      and `AccountFilter` (`OncePerRequestFilter`, binds header then clears in
      finally)
- [x] 1.3 Add `AccountAutoConfiguration` and register it in shared
      `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [x] 1.4 Unit test: account context binds from header and is cleared per
      request (no leak across requests) ← (verify: filter clears context in
      finally even on downstream exception)

## 2. Proto event contracts

- [x] 2.1 Add `services/proto/.../event/meet/v1/meeting_snapshot.proto` (shared
      snapshot message with `MeetingSettings` and `IssueLink`)
- [x] 2.2 Add `event/meet/v1/meeting_created.proto` (imports snapshot, replaces
      former `meeting_scheduled.proto`)
- [x] 2.3 Add `event/meet/v1/meeting_started.proto` (imports snapshot +
      livekit_room_name + started_at)
- [x] 2.4 Add `event/meet/v1/meeting_invitations_sent.proto` (invitees with
      embedded token, no token map)
- [x] 2.5 Regenerate protos and confirm Buf `STANDARD` lint passes ← (verify:
      `pnpm run openapi`/buf clean; generated classes compile)

## 3. Domain additions

- [x] 3.1 Add `InviteTokenGenerator` outbound port in `meet/domain/port`
      (returns raw token + SHA-256 hash + expiry)
- [x] 3.2 Add `Meeting.recordInvitationsSent(...)` registering
      `MeetingInvitationsSentEvent` from invitee snapshots (each carrying its
      own token)
- [x] 3.3 Unit test: `recordInvitationsSent` registers the event with correct
      invitees and embedded tokens; no event when list empty ← (verify: event
      registered on aggregate, matches MeetingInvitationsSentEvent shape)

## 4. Persistence adapters

- [x] 4.1 `MeetingJpaRepository`, `MeetingPersistenceMapper` (entity⇄domain
      incl. issueLink/type/status/settings), `MeetingRepositoryAdapter`
      (implement `save`/`findById`/`existsByShortCode`; unused query methods
      throw `UnsupportedOperationException` with TODO)
- [x] 4.2 `ParticipationLogJpaRepository`, `ParticipationLogPersistenceMapper`,
      `ParticipationLogRepositoryAdapter` (implement `save`; unused methods
      throw `UnsupportedOperationException`)
- [x] 4.3 `MeetingInviteeJpaRepository`, `MeetingInviteePersistenceMapper`
      (incl. inline token_* columns), `MeetingInviteeRepositoryAdapter`
      (implement `saveAll`/`save`; unused methods throw
      `UnsupportedOperationException`)
- [x] 4.4 `OutboxEventJpaRepository` (`claimBatch` FOR UPDATE SKIP LOCKED,
      `markPublished`) and `OutboxStoreRepositoryAdapter implements OutboxStore`

## 5. LiveKit adapter

- [x] 5.1 Add `LiveKitProperties` (`app.livekit.*`) and `LiveKitConfig`
- [x] 5.2 Implement `LiveKitAdapter implements LiveKitPort#generateToken` using
      the LiveKit server SDK (HOST grants, room `meeting-<id>`, identity,
      attributes); map SDK failure to `MeetingError.LiveKitUnavailable`

## 6. Messaging adapters

- [x] 6.1 `KafkaProducerConfig` (CloudEvent producer factory +
      `KafkaTemplate<String, CloudEvent>`)
- [x] 6.2 Three `OutboxEventProtoMapper` impls (created, started,
      invitations-sent) mapping domain events → proto with full snapshot
- [x] 6.3 `OutboxEventPublisher implements EventPublisher` (proto mapper +
      `CloudEventEncoder`, tenant from `TenantContext`, write `outbox_event`
      row)
- [x] 6.4 Add `smiski.outbox` config (transport kafka, relay enabled,
      `cloudevent.source: meet-service`) and account-header config to
      `services/meet/src/main/resources/application.yaml`

## 7. Invite-token infrastructure

- [x] 7.1 Implement `InviteTokenJwtGenerator implements InviteTokenGenerator`
      (JJWT, `zms.invite.token-secret`/`token-expiry-days`, SHA-256 hash)

## 8. Application layer

- [x] 8.1 Add `CreateInstantMeetingCommand`, `CreateInstantMeetingResult`, and
      result mapper (domain→result incl. livekit token/roomName)
- [x] 8.2 Add `CreateInstantMeetingUseCase` interface (extends shared `UseCase`)
- [x] 8.3 Implement `CreateInstantMeetingApplicationService`
      (`@Service @Transactional`): unique shortCode retry,
      `instant()`+`start()`, host `ParticipationLog.join`, invitee create+token,
      `recordInvitationsSent`, persist all, enqueue events, mint LiveKit HOST
      token
- [x] 8.4 Unit test: successful creation yields LIVE meeting + host session +
      invitees + registered events (mocked ports) ← (verify: orchestration order
      and that all PublishableEvents reach EventPublisher)
- [x] 8.5 Unit test: short-code collision retries then exhausts; LiveKit failure
      returns `LiveKitUnavailable`

## 9. Presentation layer

- [x] 9.1 Add request DTOs (`CreateInstantMeetingRequest` + nested
      settings/host/invitee/issueLink) with Jakarta validation and
      `toCommand(accountId, tenantId)`
- [x] 9.2 Add `CreateInstantMeetingResponse` (`meeting` snapshot + `livekit`)
      with `from(result)`; snapshot excludes tenantId
- [x] 9.3 Add `MeetingController` `@PostMapping("/meetings:instant")` reading
      `AccountContext`/`TenantContext`, returning `201 Created` + Location via
      `ResultResponder`
- [x] 9.4 Add OpenAPI annotations (`@Operation`/`@ApiResponse` incl. 400
      problem+json examples)

## 10. Integration tests & verification

- [x] 10.1 Controller `@SpringBootTest`: valid request → 201 with snapshot +
      host token, tenantId absent (Scenario: successful instant creation)
- [x] 10.2 Controller test: missing `X-Account-Id` rejected; missing settings /
      host fields / invalid invitee email → 400 VALIDATION_ERROR, nothing
      persisted (Scenarios: missing host header, validation errors)
- [x] 10.3 Persistence/integration test: created meeting is INSTANT+LIVE with
      one active host log; invitees persisted PENDING with hashed tokens; base
      outbox rows present, invitations row only when invitees exist (Scenarios:
      lifecycle, invitee registration, event publication)
- [x] 10.4 Integration test: LiveKit failure and persistence failure both roll
      back — no meeting/log/invitee/outbox rows (Scenarios: LiveKit unavailable,
      persistence failure) ← (verify: transactional rollback leaves zero rows
      across all four tables)
- [x] 10.5 Regenerate `services/meet/openapi.yaml` via
      `generateOpenApiDocsFromTests` and run `pnpm run openapi`
- [x] 10.6 Run `./services/gradlew spotlessApply` and
      `./services/gradlew -p services/ meet build` (unit + ArchUnit +
      integration) green ← (verify: ArchUnit layering + naming rules pass with
      the new adapters)

## 11. Contract adjustments (post-implementation)

- [x] 11.1 Gom `accountId` vào Host record:
      `CreateInstantMeetingCommand.Host(accountId, displayName, deviceId)`, bỏ
      `accountId` top-level. Request `toCommand` nhét accountId từ header vào
      Host. Service đọc `command.host().accountId()`.
- [x] 11.2 Thêm `description` field: Request (@NotBlank), Command, Result,
      Response, service truyền vào `Meeting.instant(...)` thay null.
- [x] 11.3 Siết non-null toàn cục cho title/description/issueLink: Request
      (title @NotBlank, description @NotBlank, issueLink @NotNull @Valid),
      Command (non-null), Domain (fields + factories + getters non-Optional),
      Events (bỏ @Nullable), Proto mappers (bỏ null guards), Entity
      (nullable=false), PersistenceMapper (trực tiếp), Migration (NOT NULL).
      Result + Response (non-null, bỏ @Nullable).
- [x] 11.4 Max participants ≤100: Request @Max(100), Domain MeetingSettings
      compact ctor validates [2..100].
- [x] 11.5 Tests cập nhật: unit test command shape mới, domain test non-null
      factory args, integration tests include required fields, thêm tests
      (missing description → 400, missing issueLink → 400, maxParticipants > 100
      → 400).
- [x] 11.6 Spec artifacts đồng bộ: spec.md, design.md, proposal.md cập nhật cho
      khớp contract mới.
- [x] 11.7 Verify: spotlessApply pass, build (unit + ArchUnit + integrationTest)
      pass, openapi regenerated + lint pass.

## 12. Host avatar attribute

- [x] 12.1 Add optional `@Nullable String avatarUrl` to
      `CreateInstantMeetingRequest.Host` record with `@Schema(nullable = true)`;
      update `toCommand` to pass it through
- [x] 12.2 Add `@Nullable String avatarUrl` as 4th component of
      `CreateInstantMeetingCommand.Host` record
- [x] 12.3 Application service passes `command.host().avatarUrl()` into
      `ParticipantAttributes` constructor (replaces hardcoded `null`)
- [x] 12.4 LiveKit adapter uses `token.getAttributes().putAll(...)` to set real
      participant attributes instead of `token.setMetadata(map.toString())`
- [x] 12.5 Unit test: success case uses non-null avatarUrl, asserts
      `LiveKitTokenRequest.participantAttributes().avatarUrl()` matches; failure
      cases use null
- [x] 12.6 Integration test: happy-path request includes `host.avatarUrl`;
      existing validation tests omit it (proving it's optional)
- [x] 12.7 Spec artifacts synced: spec.md (host object description + avatar
      attribute scenarios), design.md (D8 decision), tasks.md (this section)

## 13. Defer host participation log to webhook

- [x] 13.1 Remove `ParticipationLogRepository` field, constructor parameter, and
      `participationLogRepository.save(hostLog)` from
      `CreateInstantMeetingApplicationService`; remove
      `ParticipationLog.join(...)` host log creation block (keep `hostIdentity`
      for LiveKit token)
- [x] 13.2 Unit test: remove `participationLogRepository` mock/stub/verify;
      rename test method to drop "host session" wording
- [x] 13.3 Integration test (`MeetingControllerIntegrationTest`): delete
      host-log query+assertions from lifecycle test; rename to
      `successfulCreation_persistsMeetingAsInstantLiveWithoutHostLog`; remove
      all `participation_logs` count checks from validation tests
- [x] 13.4 Integration test (`MeetingCreationRollbackIntegrationTest`): remove
      `participation_logs` count lines and update class javadoc
- [x] 13.5 Spec artifacts synced: spec.md (lifecycle requirement reworded,
      scenarios updated), proposal.md (removed host log clause), design.md
      (D6/D9/sequence updated), tasks.md (this section)
