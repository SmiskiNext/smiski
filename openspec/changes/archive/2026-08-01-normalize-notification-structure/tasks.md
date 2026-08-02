## 1. Domain Layer — Error model and new port

- [x] 1.1 Create `domain/NotificationError.java` — sealed interface extending
      shared `DomainError`, with record `InvalidSignature`
- [x] 1.2 Create `domain/NotificationErrorCode.java` — enum implementing shared
      `ErrorCode` with value `INVALID_SIGNATURE`
- [x] 1.3 Create `domain/port/SseRelayPort.java` — pure-Java interface with
      `void pushJoinRequestCreated(UUID meetingId, PendingJoinRequest request)`
      and `void pushJoinResolved(UUID requestId, JoinDecision decision)` ←
      (verify: no `org.springframework.*` import; methods match
      SseConnectionManager push methods)

## 2. Application Layer — Shell (ports, use cases, commands, results)

- [x] 2.1 Create `application/SseSubscriptionPort.java` — interface with
      `SseEmitter subscribe(UUID meetingId)` and
      `SseEmitter subscribeRequest(UUID requestId)`
- [x] 2.2 Create `application/usecase/RelayJoinCreatedUseCase.java` extending
      `UseCase<RelayJoinCreatedCommand, RelayJoinCreatedResult, NotificationError>`
- [x] 2.3 Create `application/usecase/RelayJoinResolvedUseCase.java` extending
      `UseCase<RelayJoinResolvedCommand, RelayJoinResolvedResult, NotificationError>`
- [x] 2.4 Create `application/usecase/SendMeetingInvitationEmailUseCase.java`
      extending
      `UseCase<SendMeetingInvitationEmailCommand, SendMeetingInvitationEmailResult, NotificationError>`
- [x] 2.5 Create `application/usecase/SendMeetingInfoUpdatedEmailUseCase.java`
      extending
      `UseCase<SendMeetingInfoUpdatedEmailCommand, SendMeetingInfoUpdatedEmailResult, NotificationError>`
- [x] 2.6 Create `application/usecase/SendInviteeRespondedEmailUseCase.java`
      extending
      `UseCase<SendInviteeRespondedEmailCommand, SendInviteeRespondedEmailResult, NotificationError>`
- [x] 2.7 Create `application/usecase/ProcessInboundEmailReplyUseCase.java`
      extending
      `UseCase<ProcessInboundEmailReplyCommand, ProcessInboundEmailReplyResult, NotificationError>`
- [x] 2.8 Create `application/usecase/SubscribeMeetingEventsUseCase.java`
      extending
      `UseCase<SubscribeMeetingEventsCommand, SubscribeMeetingEventsResult, NotificationError>`
- [x] 2.9 Create command records in `application/command/`:
      `RelayJoinCreatedCommand`, `RelayJoinResolvedCommand`,
      `SendMeetingInvitationEmailCommand`, `SendMeetingInfoUpdatedEmailCommand`,
      `SendInviteeRespondedEmailCommand`,
      `ProcessInboundEmailReplyCommand(String payload, Map<String,String> headers)`,
      `SubscribeMeetingEventsCommand(UUID meetingId, boolean isRequestStream, UUID requestId)`
- [x] 2.10 Rename directory `application/response/` → `application/result/` (or
      create `application/result/` and remove the old empty dir)
- [x] 2.11 Create result records in `application/result/`:
      `RelayJoinCreatedResult`, `RelayJoinResolvedResult`,
      `SendMeetingInvitationEmailResult`, `SendMeetingInfoUpdatedEmailResult`,
      `SendInviteeRespondedEmailResult`, `ProcessInboundEmailReplyResult`,
      `SubscribeMeetingEventsResult(SseEmitter emitter)` ← (verify: no
      `@JsonProperty`/`@Schema`/`@JsonIgnore` in any result record)

## 3. Application Layer — Service implementations

- [x] 3.1 Create `application/service/RelayJoinCreatedApplicationService.java` —
      `@Service`, injects `PendingJoinRequestStore` and `SseRelayPort`; calls
      `store.upsert(command.request())` then
      `sseRelayPort.pushJoinRequestCreated(command.meetingId(), command.request())`
- [x] 3.2 Create `application/service/RelayJoinResolvedApplicationService.java`
      — `@Service`, injects `JoinDecisionStore` and `SseRelayPort`; calls
      `store.upsert(command.decision())` then
      `sseRelayPort.pushJoinResolved(command.requestId(), command.decision())`
- [x] 3.3 Create
      `application/service/SendMeetingInvitationEmailApplicationService.java` —
      `@Service`, injects `EmailSender`; sends calendar invitation email using
      `CalendarEmail` from command
- [x] 3.4 Create
      `application/service/SendMeetingInfoUpdatedEmailApplicationService.java` —
      `@Service`, injects `EmailSender`; sends meeting info update email
- [x] 3.5 Create
      `application/service/SendInviteeRespondedEmailApplicationService.java` —
      `@Service`, injects `EmailSender`; sends invitee response confirmation
      email
- [x] 3.6 Create
      `application/service/ProcessInboundEmailReplyApplicationService.java` —
      `@Service`, injects `WebhookVerifier` and `InboundEmailReplyProcessor`;
      verifies signature first, returns
      `Result.failure(new NotificationError.InvalidSignature())` on failure,
      otherwise delegates to
      `InboundEmailReplyProcessor.processInboundEmail(payload)`
- [x] 3.7 Create
      `application/service/SubscribeMeetingEventsApplicationService.java` —
      `@Service`, injects `SseSubscriptionPort`; calls
      `port.subscribe(meetingId)` or `port.subscribeRequest(requestId)` and
      wraps in `SubscribeMeetingEventsResult` ← (verify: all 7
      ApplicationServices compile with `@Service`; none imports concrete
      infrastructure class)

## 4. Infrastructure — Move SseConnectionManager and config

- [x] 4.1 Copy `application/sse/SseConnectionManager.java` to
      `infrastructure/sse/SseConnectionManager.java`; update package declaration
      to `infrastructure.sse`; add
      `implements SseRelayPort, SseSubscriptionPort`; replace `@Component` with
      `@Service` (or keep `@Component` — must satisfy ArchUnit); update
      `pushJoinRequestCreated` and `pushJoinResolved` to satisfy `SseRelayPort`
      signatures; expose `subscribe` and `subscribeRequest` to satisfy
      `SseSubscriptionPort`
- [x] 4.2 Copy `application/sse/SseProperties.java` to
      `infrastructure/config/SseProperties.java`; update package declaration
- [x] 4.3 Update `NotificationApplication.java`: change
      `@EnableConfigurationProperties` entry from
      `application.sse.SseProperties` to `infrastructure.config.SseProperties`
- [x] 4.4 Move SSE data DTOs `JoinRequestCreatedData`,
      `JoinRequestApprovedData`, `JoinRequestDeniedData` from `application/sse/`
      to `presentation/response/`; update package declarations ← (verify:
      `SseConnectionManager` in `infrastructure/sse/` compiles and still
      references `presentation.response.*Data` DTOs correctly)
- [x] 4.5 Rename `infrastructure/email/InboundEmailReplyService.java` →
      `InboundEmailReplyProcessorAdapter.java`; update class name, Spring bean
      name (if any), and all references

## 5. Infrastructure — Update Kafka consumers

- [x] 5.1 Update `JoinCreatedEventConsumer`: replace `SseConnectionManager` and
      `PendingJoinRequestStore` injections with `RelayJoinCreatedUseCase`; call
      `useCase.execute(new RelayJoinCreatedCommand(...))` passing the decoded
      `PendingJoinRequest`; keep the existing proto decode logic unchanged
- [x] 5.2 Update `JoinResolvedEventConsumer`: replace `SseConnectionManager` and
      `JoinDecisionStore` injections with `RelayJoinResolvedUseCase`; call
      `useCase.execute(new RelayJoinResolvedCommand(...))` passing the decoded
      `JoinDecision`; keep the existing proto decode logic unchanged
- [x] 5.3 Update `MeetingInvitationsCreatedEmailConsumer`: inject
      `SendMeetingInvitationEmailUseCase` and delegate to it via Command
- [x] 5.4 Update `MeetingInfoUpdatedEmailConsumer`: inject
      `SendMeetingInfoUpdatedEmailUseCase` and delegate to it via Command
- [x] 5.5 Update `InviteeRespondedEmailConsumer`: inject
      `SendInviteeRespondedEmailUseCase` and delegate to it via Command ←
      (verify: no consumer class imports anything from `application.sse.*` or
      `infrastructure.sse.*` directly)

## 6. Presentation — Update controllers and add sub-packages

- [x] 6.1 Update `MeetingEventsController`: replace `SseConnectionManager` field
      with `SubscribeMeetingEventsUseCase`; call
      `useCase.execute(new SubscribeMeetingEventsCommand(id, false, null)).emitter()`
      for `subscribe` and
      `useCase.execute(new SubscribeMeetingEventsCommand(id, true, requestId)).emitter()`
      for `subscribeRequest`
- [x] 6.2 Update `ResendInboundWebhookController`: replace `WebhookVerifier` and
      `InboundEmailReplyProcessor` fields with single
      `ProcessInboundEmailReplyUseCase`; call
      `useCase.execute(new ProcessInboundEmailReplyCommand(payload, headers))`
      and map `Result` — `InvalidSignature` → 400, success → 200
- [x] 6.3 Add `presentation/request/` sub-package (can be empty `.gitkeep`
      initially — the webhook endpoint uses raw `String` body per existing
      design)
- [x] 6.4 Ensure `presentation/response/` sub-package exists with the3 SSE data
      DTOs moved in task 4.4 ← (verify: `MeetingEventsController` has zero
      imports from `application.sse.*` or `infrastructure.sse.*`)

## 7. Cleanup

- [x] 7.1 Delete `application/sse/` directory and all its original files
      (`SseConnectionManager.java`, `SseProperties.java`,
      `JoinRequest*Data.java`) — only after all references have been migrated in
      tasks 4–6
- [x] 7.2 Verify no remaining import references `application.sse.*` anywhere in
      the codebase ← (verify:
      `grep -r "application.sse" services/notification/src` returns empty)

## 8. Tests

- [x] 8.1 Add `test/application/RelayJoinCreatedApplicationServiceTest.java` —
      mock `PendingJoinRequestStore` and `SseRelayPort`; assert `store.upsert`
      and `sseRelayPort.pushJoinRequestCreated` are called; assert returns
      `Result.success()`
- [x] 8.2 Add `test/application/RelayJoinResolvedApplicationServiceTest.java` —
      mock `JoinDecisionStore` and `SseRelayPort`; assert `store.upsert` and
      `sseRelayPort.pushJoinResolved` are called
- [x] 8.3 Add
      `test/application/ProcessInboundEmailReplyApplicationServiceTest.java` —
      test: (a) invalid signature returns `Result.failure(InvalidSignature)`,
      (b) valid signature delegates to `InboundEmailReplyProcessor` ← (verify:
      spec scenario "Webhook verification failure mapped via Result" is covered)
- [x] 8.4 Add
      `test/application/SubscribeMeetingEventsApplicationServiceTest.java` —
      mock `SseSubscriptionPort`; assert that `subscribe(meetingId)` is called
      and emitter is returned in result
- [x] 8.5 Add `integrationTest/NotificationOpenApiGenerationTest.java` —
      `@SpringBootTest` that runs `generateOpenApiDocsFromTests` pattern (follow
      `MeetOpenApiGenerationTest` in `services/meet/` as reference) ← (verify:
      `openapi.yaml` written with paths `/meetings/{id}/events` and
      `/webhooks/resend/inbound`)
- [x] 8.6 Add
      `test/application/SendMeetingInvitationEmailApplicationServiceTest.java` —
      mock `EmailSender`; assert `emailSender.send(command.email())` called
      once; assert `Result.success()`
- [x] 8.7 Add
      `test/application/SendMeetingInfoUpdatedEmailApplicationServiceTest.java`
      — mock `EmailSender`; assert `emailSender.send(command.email())` called
      once; assert `Result.success()`
- [x] 8.8 Add
      `test/application/SendInviteeRespondedEmailApplicationServiceTest.java` —
      mock `EmailSender`; assert `emailSender.send(command.email())` called
      once; assert `Result.success()`

## 9. Build verification

- [x] 9.1 Run `./services/gradlew spotlessApply` from repo root — fix any
      formatting issues
- [x] 9.2 Run `./services/gradlew -p services/notification test` — ArchUnit +
      all fast unit tests must pass with zero failures ← (verify:
      `ArchitectureTest` passes; no `application.sse` import violations)
- [x] 9.3 Run `./services/gradlew -p services/notification integrationTest` —
      Testcontainers + `@SpringBootTest` context load + OpenAPI generation must
      pass
