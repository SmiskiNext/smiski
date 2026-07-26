# Implementation Tasks

## 1. Proto contract + event rename (meet)

- [x] 1.1 Add
      `services/proto/src/main/proto/io/github/smiskinext/event/meet/v1/join_created.proto`
      with message `JoinCreated` (meeting_id, join_request_id, tenant_id,
      account_id, display_name, device_id, occurred_at), package
      `io.github.smiskinext.event.meet.v1`
- [x] 1.2 Run `./services/gradlew bufFormatApply` and lint the proto module (Buf
      `STANDARD`) ← (verify: join_created.proto passes Buf STANDARD lint and
      generates the JoinCreated Java type)
- [x] 1.3 Rename `JoinRequestCreatedEvent#topic()` to `meet.join.created` and
      `#eventType()` to `io.github.smiskinext.meet.join.created.v1`
- [x] 1.4 Add `JoinCreatedEventProtoMapper` in `meet/infrastructure/messaging`
      implementing `OutboxEventProtoMapper<JoinRequestCreatedEvent>` (eventType,
      dataSchema `io.github.smiskinext.event.meet.v1.JoinCreated`, toProto) ←
      (verify: mapper is a @Component and resolvable by
      OutboxEventProtoMapperRegistry for JoinRequestCreatedEvent)

## 2. Redis join-request adapter + config (meet)

- [x] 2.1 Add `meet/infrastructure/config/RedisConfig` with
      `StringRedisTemplate` and a typed `RedisTemplate` using Jackson 3
      `JsonMapper` (`tools.jackson.*`) +
      `GenericJacksonJsonRedisSerializer`/`JacksonJsonRedisSerializer` and a
      `PolymorphicTypeValidator` restricted to the meet domain package, guarded
      by `@ConditionalOnProperty("spring.data.redis.host")`
- [x] 2.2 Implement `JoinRequestRedisRepositoryAdapter` (`@Repository`,
      implements `JoinRequestRepository`): ZSET `join_request:{meetingId}`, meta
      `join_request_meta:{requestId}`, device index
      `join_request_device:{meetingId}:{deviceId}`; atomic Lua scripts for
      save/removeFromQueue/updateStatus/deleteAllByMeetingId; request TTL + ZSET
      buffer
- [x] 2.3 Implement `findById`, `findByDeviceId`, `findPendingByMeetingId`,
      `findPendingSummariesByMeetingId` reads via the typed template ← (verify:
      multi-key save/remove are atomic Lua so queue/meta/device never diverge;
      matches JoinRequestRepository port contract)
- [x] 2.4 Ensure `spring.data.redis.*` exists in meet `application.yaml` /
      `application-dev.yaml` (host/port/password)

## 3. Join use case + application layer (meet)

- [x] 3.1 Add `RequestJoinCommand` (meetingId, tenantId, accountId, displayName,
      deviceId) and `RequestJoinResult` (requestId, status, nullable token,
      nullable roomName)
- [x] 3.2 Add `RequestJoinUseCase` interface extending
      `UseCase<RequestJoinCommand, RequestJoinResult, MeetingError>`
- [x] 3.3 Implement `RequestJoinApplicationService` (`@Service @Transactional`):
      load meeting; branch on `admissionPolicy`
- [x] 3.4 ALLOW_ALL branch: `findActiveByIdWithLock`, capacity check via
      `countActiveByMeetingId` → `MeetingFull`, `LiveKitPort.generateToken` as
      PARTICIPANT, save `ParticipationLog.join(...)`, return APPROVED result ←
      (verify: capacity enforced under the pessimistic lock so concurrent joins
      cannot exceed maxParticipants)
- [x] 3.5 MANUAL_APPROVAL branch: idempotent existing-device lookup; else create
      `JoinRequest` (5m TTL) → `joinRequestRepository.save`, register
      `JoinRequestCreated` event on the aggregate, drain via shared
      `EventPublisher`, return PENDING result ← (verify: exactly one
      meet.join.created enqueued in the same tx; duplicate device returns
      existing request)

## 4. Join endpoint (meet presentation)

- [x] 4.1 Add `JoinMeetingRequest` DTO (`@NotBlank @Size(max=100) displayName`,
      `@NotBlank deviceId`) with `toCommand(meetingId, accountId, tenantId)`
- [x] 4.2 Add `JoinMeetingResponse` DTO (requestId, status, token, roomName)
      with static `from(RequestJoinResult)`
- [x] 4.3 Add `@PostMapping("/meetings/{id}:join")` to `MeetingController`,
      resolve account/tenant from context, return
      `responder.ok(result.map(JoinMeetingResponse::from))` ← (verify: route is
      /api/{version}/meetings/{id}:join; 200 with status field for both
      APPROVED/PENDING; problem+json on failure)

## 5. Notification SSE infrastructure

- [x] 5.1 Add `notification/infrastructure/config/SseProperties` (host stream
      timeout, heartbeat interval) and `RedisConfig` (Jackson 3 serializer,
      `@ConditionalOnProperty("spring.data.redis.host")`) ← (deviation:
      `SseProperties`+`SseConnectionManager` live in `application/sse` so both
      presentation and the infra consumer can depend on them without violating
      the intra-service `presentation→infrastructure` /
      `application→infrastructure` ArchUnit rules; `RedisConfig` stays in
      `infrastructure/config`)
- [x] 5.2 Add `SseConnectionManager`
      (`ConcurrentHashMap<meetingId, CopyOnWriteArrayList<SseEmitter>>`,
      register/remove, single daemon heartbeat scheduler,
      timeout/completion/error cleanup, push-to-meeting)
- [x] 5.3 Add `PendingJoinRequestStore` (Redis, keyed per meeting, TTL aligned
      to join lifetime) with upsert + read-unexpired ← (verify: replay reads
      only unexpired pending requests, shared across replicas)
- [x] 5.4 Add `notification/infrastructure/security/SecurityConfig` (stateless,
      permitAll, matches meet's trust-boundary model)

## 6. Notification consumer + endpoint

- [x] 6.1 Add `notification/infrastructure/config/KafkaConfig` CloudEvent
      consumer factory; consumer group id derived per-instance so every replica
      receives every event
- [x] 6.2 Add Kafka consumer for topic `meet.join.created`: decode CloudEvent,
      read `data` JSON fields with Jackson, upsert `PendingJoinRequestStore`,
      push `join_request_created` to local emitters; malformed message handled
      without terminating the consumer ← (verify: per-replica delivery;
      malformed event does not break the consumer)
- [x] 6.3 Add SSE controller `GET /meetings/{id}/events` (produces
      `text/event-stream`): create emitter via `SseConnectionManager`, replay
      pending from `PendingJoinRequestStore` ← (verify: live push reaches
      locally held emitter; late subscriber replays pending; heartbeat keeps
      idle stream open; timeout completes and cleans up)
- [x] 6.4 Add `spring.data.redis.*` to notification `application.yaml` /
      `application-dev.yaml`; add Valkey testcontainer to
      `notification/build.gradle.kts` ← (note: Valkey testcontainer support
      already resolves transitively via `testFixtures(shared)`;
      `ValkeyContainerSupport` is used by the existing
      `TestcontainersConfiguration`, so no new gradle dependency is required)

## 7. Tests — meet (from spec scenarios)

- [x] 7.1 Unit (`RequestJoinApplicationServiceTest`): validation-independent
      branches — ALLOW_ALL admits (APPROVED + token + participation log),
      meeting-at-capacity → MeetingFull, unknown meeting → not-found,
      MANUAL_APPROVAL creates pending + registers event, duplicate device
      idempotent (mock ports)
- [x] 7.2 Integration (`JoinRequestRedisRepositoryAdapter` on Valkey
      testcontainer): pending queue ordered by requested time; atomic removal
      clears queue+meta+device; TTL expiry removes request from queue ← (verify:
      covers join-meeting Redis storage scenarios)
- [x] 7.3 Integration (`MeetingController :join` on Postgres+Valkey+Kafka
      testcontainers): blank field → 400 VALIDATION_ERROR/REQUIRED; ALLOW_ALL →
      200 APPROVED; at-capacity → 409 meeting-full; MANUAL_APPROVAL → 200
      PENDING + meet.join.created enqueued ← (verify: all join-meeting
      endpoint + admission scenarios pass end-to-end)
- [x] 7.4 Regenerate meet OpenAPI (`generateOpenApiDocsFromTests`) and confirm
      `:join` appears

## 8. Tests — notification (from spec scenarios)

- [x] 8.1 Unit (`SseConnectionManager`): live push reaches registered emitter;
      timeout/cleanup removes emitter + heartbeat task; heartbeat comment
      emitted on idle
- [x] 8.2 Integration (`JoinCreatedEventConsumerIntegrationTest` on Kafka+Valkey
      testcontainers): consumed meet.join.created retained in the store for
      replay; expired pending not replayed; malformed event does not break the
      consumer (subsequent well-formed event still consumed) ← (verify: covers
      join-request-notification consume + replay scenarios; live-emitter push
      covered by the `SseConnectionManager` unit test; per-replica delivery
      reasoned via per-instance `notification-join-sse-<uuid>` group config)

## 9. Verification gate

- [x] 9.1 `./services/gradlew spotlessApply bufFormatApply` — spotless from
      root; `bufFormatApply` runs in the `services/proto` included build
      (`./services/gradlew -p services/proto bufFormatApply`); proto also passes
      `bufLint`
- [x] 9.2 `./services/gradlew -p services/meet build` (test + integrationTest) —
      GREEN
- [x] 9.3 `./services/gradlew -p services/notification build` (test +
      integrationTest) — GREEN
- [x] 9.4 `pnpm run openapi` (regenerate + lint service specs) — GREEN (`:join`
      present in `services/meet/openapi.yaml` and merged
      `services/openapi.yaml`; only pre-existing unused-component warnings) ←
      (verify: all builds green, OpenAPI lints clean, no ArchUnit violations)

## 10. Enhancement — avatarUrl end-to-end + role in token + deferred participation log

- [x] 10.1 Proto: add `string avatar_url = 8;` to `join_created.proto` (passes
      Buf `STANDARD` lint)
- [x] 10.2 meet presentation: `JoinMeetingRequest` gains optional
      `@Nullable avatarUrl`; `toCommand` forwards it
- [x] 10.3 meet application: `RequestJoinCommand` gains `avatarUrl`
- [x] 10.4 meet application: `RequestJoinApplicationService.admitImmediately`
      builds `ParticipantAttributes(command.avatarUrl(), PARTICIPANT)` so the
      LiveKit token carries `role` + `avatarUrl`
- [x] 10.5 meet application: `admitImmediately` no longer writes a participation
      log (deferred to `participant_joined` webhook); capacity check via
      `countActiveByMeetingId` retained; `requestId` is a generated UUIDv7
      correlation id
- [x] 10.6 meet application: `createPendingRequest` forwards `avatarUrl` into
      `JoinRequest.create`
- [x] 10.7 meet domain: `JoinRequest` carries `@Nullable avatarUrl`
      (ctor/create/reconstitute/getter) and passes it into
      `JoinRequestCreatedEvent`
- [x] 10.8 meet domain: `JoinRequestCreatedEvent` gains `@Nullable avatarUrl`
- [x] 10.9 meet infrastructure: `JoinCreatedEventProtoMapper` sets `avatar_url`
      (null → empty string)
- [x] 10.10 meet infrastructure: `JoinRequestData` Redis model round-trips
      `avatarUrl`
- [x] 10.11 notification domain: `PendingJoinRequest` gains
      `@Nullable avatarUrl` (not requireNonNull)
- [x] 10.12 notification application: `JoinRequestCreatedData` SSE payload gains
      `@Nullable avatarUrl`
- [x] 10.13 notification infrastructure: `PendingJoinRequestData` Redis model
      round-trips `avatarUrl`
- [x] 10.14 notification infrastructure: `JoinCreatedEventConsumer` reads
      `avatarUrl` (empty → null) and forwards to store + SSE push
- [x] 10.15 notification application: `SseConnectionManager` replay forwards
      `avatarUrl`
- [x] 10.16 Tests updated: meet unit asserts token attributes carry
      avatarUrl+role, no participation-log save, non-null requestId, event
      carries avatarUrl; meet integration `activeCount == 0` and avatarUrl in
      body; meet Redis adapter round-trips avatarUrl; notification consumer/SSE
      tests carry avatarUrl
- [x] 10.17 Regenerate meet OpenAPI — `avatarUrl` present in
      `JoinMeetingRequest` schema
