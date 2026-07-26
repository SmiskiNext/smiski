## 1. Proto contracts (meet events)

- [x] 1.1 Add
      `services/proto/src/main/proto/io/github/smiskinext/event/meet/v1/join_approved.proto`
      (`JoinApproved`: meeting_id, join_request_id, tenant_id, account_id,
      device_id, live_kit_token, room_name, approved_by, occurred_at)
- [x] 1.2 Add `join_denied.proto` (`JoinDenied`: meeting_id, join_request_id,
      tenant_id, account_id, device_id, denied_by, occurred_at)
- [x] 1.3 Run `./services/gradlew -p services/proto bufFormatApply` and build
      proto ← (verify: both protos pass Buf `STANDARD` lint and generate Java
      classes)

## 2. Meet domain — event rename + registration helpers

- [x] 2.1 In `JoinRequestApprovedEvent`: change topic to `meet.join.approved`,
      eventType to `io.github.smiskinext.meet.join.approved.v1`, add `deviceId`
      field, `implements SseTriggeringEvent`
- [x] 2.2 In `JoinRequestDeniedEvent`: change topic to `meet.join.denied`,
      eventType to `io.github.smiskinext.meet.join.denied.v1`, add `deviceId`
      field, `implements SseTriggeringEvent`
- [x] 2.3 Add
      `JoinRequest.registerApprovedEvent(tenantId, liveKitToken, roomName, approvedBy)`
      and `registerDeniedEvent(tenantId, deniedBy)` mirroring
      `registerCreatedEvent` ← (verify: approve/deny transitions unchanged;
      events carry deviceId from the aggregate)

## 3. Meet infrastructure — result store + proto mappers

- [x] 3.1 Add `JoinRequestResultStoreRedisAdapter` implementing
      `JoinRequestResultStore` (key `join_request_result:{requestId}`, JSON via
      `RedisConfig` JsonMapper, TTL 5m + buffer)
- [x] 3.2 Add `JoinApprovedEventProtoMapper` mapping `JoinRequestApprovedEvent`
      → `JoinApproved`
- [x] 3.3 Add `JoinDeniedEventProtoMapper` mapping `JoinRequestDeniedEvent` →
      `JoinDenied` ← (verify: both registered as `OutboxEventProtoMapper` beans;
      unmapped event would fail enqueue)

## 4. Meet application — accept/decline use cases

- [x] 4.1 Add `AcceptJoinRequestsCommand` and `DeclineJoinRequestsCommand`
      (meetingId, tenantId, accountId, requestIds)
- [x] 4.2 Add result records: per-item `JoinDecisionItemResult` (requestId,
      status, token?, roomName?, reason?) and `AcceptJoinRequestsResult` /
      `DeclineJoinRequestsResult` wrapping the item list
- [x] 4.3 Add `AcceptJoinRequestsUseCase` / `DeclineJoinRequestsUseCase`
      interfaces
- [x] 4.4 Implement `AcceptJoinRequestsApplicationService`: host+existence check
      under `findActiveByIdWithLock`, count active seats, per-item loop
      (validate PENDING+meeting match+not expired, capacity gate, generate
      PARTICIPANT token, `approve()`, `resultStore.save`,
      `registerApprovedEvent`, `publishEventsOf`, `removeFromQueue`),
      best-effort item results ← (verify: capacity never exceeds maxParticipants
      across the batch; token room/metadata match join path)
- [x] 4.5 Implement `DeclineJoinRequestsApplicationService`: host+existence
      check, per-item loop (`deny()`, `resultStore.save`, `registerDeniedEvent`,
      publish, `removeFromQueue`), best-effort item results
- [x] 4.6 Add mapper(s) from domain/application to per-item results ← (verify:
      FAILED items carry reason and no token; APPROVED carry token+roomName)

## 5. Meet presentation — endpoints

- [x] 5.1 Add request DTO `HandleJoinRequestsRequest`
      (`@NotEmpty List<@NotNull UUID> requestIds`) with `toCommand(...)`
- [x] 5.2 Add response DTOs (`JoinDecisionResponse` with `results` list,
      per-item entry) with `from(...)` factories
- [x] 5.3 Add `POST /meetings/{id}/join-requests:accept` and `:decline` handlers
      to `MeetingController` (account context, tenant context,
      `ResultResponder.ok`, `@Operation`/`@ApiResponses` with 200/400/403/404
      examples) ← (verify: routes resolve to
      `/api/{version}/meetings/{id}/join-requests:accept|decline`; missing
      account → 400, non-host → 403 NOT_OWNER, unknown meeting → 404)

## 6. Notification — restore join-created consumer

- [x] 6.1 Add `JoinCreatedEventConsumer` (`infrastructure/messaging`) on
      `cloudEventKafkaListenerContainerFactory`, `topics = meet.join.created`,
      decode CloudEvent `data` JSON with Jackson, upsert
      `PendingJoinRequestStore`, call
      `sseConnectionManager.pushJoinRequestCreated`; malformed messages logged
      and skipped ← (verify: consumed event reaches locally held host emitter;
      malformed event does not terminate the consumer)

## 7. Notification — requester decision delivery

- [x] 7.1 Add domain `JoinDecision` model + `JoinDecisionStore` port (keyed by
      requestId, APPROVED retains token/roomName)
- [x] 7.2 Add Redis adapter for `JoinDecisionStore` (key
      `join_decision_meta:{requestId}`, TTL aligned to requester window) +
      `RedisConfig` template/serializer
- [x] 7.3 Extend `SseConnectionManager` with `emittersByRequest`,
      `subscribeRequest(requestId)` (initial heartbeat, scheduled heartbeat,
      replay recorded decision), `pushJoinResolved(requestId, data)`, and
      cleanup on completion/timeout/error
- [x] 7.4 Add SSE payload records (`JoinRequestApprovedData` {token, roomName},
      `JoinRequestDeniedData` {reason?})
- [x] 7.5 Add `JoinResolvedEventConsumer` subscribing to `meet.join.approved` +
      `meet.join.denied`, decode CloudEvent data, upsert `JoinDecisionStore`,
      push to requester emitters; malformed skipped
- [x] 7.6 Add `GET /meetings/{id}/join-requests/{requestId}/events`
      (`text/event-stream`) returning the requester emitter ← (verify: live
      approval/denial reaches held emitter; late subscribe replays recorded
      decision; stream times out and cleans up)

## 8. Meet tests (from spec scenarios)

- [x] 8.1 Application test — accept single pending request approves with token,
      persists outcome, dequeues
- [x] 8.2 Application test — accept batch exceeding capacity: only fitting
      requests APPROVED, rest FAILED meeting-full, count ≤ maxParticipants
- [x] 8.3 Application test — accept batch with unknown/terminal/expired ids
      yields per-item FAILED with reasons, others APPROVED
- [x] 8.4 Application test — decline pending request DENIED, persists outcome,
      dequeues; best-effort per item
- [x] 8.5 Application test — non-host caller → NotOwner; unknown meeting →
      MeetingNotFound
- [x] 8.6 Infrastructure integration test — `JoinRequestResultStoreRedisAdapter`
      save/read approved (token retained) and denied (no token) with TTL
- [x] 8.7 Presentation integration test — accept/decline endpoints: 200 per-item
      results, 400 empty body, 403 non-host, 404 unknown meeting ← (verify:
      covers host-join-decision spec scenarios; problem+json shapes correct)

## 9. Notification tests (from spec scenarios)

- [x] 9.1 Unit test — `SseConnectionManager` requester stream: live
      approved/denied push reaches registered emitter; replay of recorded
      decision on subscribe; cleanup on completion
- [x] 9.2 Integration test — `JoinResolvedEventConsumer` on Kafka+Valkey:
      approved/denied consumed → decision stored + pushed; malformed event does
      not break consumer
- [x] 9.3 Integration test — restored `JoinCreatedEventConsumer` consumes
      `meet.join.created` → pending stored + host push; malformed event skipped
      ← (verify: covers join-decision-notification + restored
      join-request-notification consume/replay scenarios)

## 10. Regenerate specs, format, build

- [x] 10.1 Regenerate meet OpenAPI:
      `./services/gradlew -p services/meet generateOpenApiDocsFromTests`
      (accept/decline endpoints present)
- [x] 10.2 `./services/gradlew spotlessApply` and `bufFormatApply`
- [x] 10.3 Build both services: `./services/gradlew -p services/meet build` and
      `-p services/notification build` ← (verify: ArchUnit passes, unit +
      integration tests green, OpenAPI regenerated)
