## Why

Participants currently have no way to join an existing meeting: the meet service
exposes create/schedule/update/delete/list/get endpoints but no join action. The
join domain model (`JoinRequest`, `JoinRequestStatus`, `AdmissionPolicy`,
`ParticipationLog`, LiveKit value objects) and the `JoinRequestCreated` event
already exist, but the Redis adapters, use case, endpoint, event proto mapper,
and the notification-side real-time delivery are all missing. Without a join
endpoint and host-facing waiting-room notifications, a scheduled or running
meeting cannot admit anyone.

## What Changes

- Add `POST /meetings/{id}:join` to the meet service. It resolves the meeting's
  admission policy and follows one of two paths:
    - `ALLOW_ALL`: immediately generate a LiveKit token, record a
      `ParticipationLog`, and return `200` with `status=APPROVED`, token, and
      room name.
    - `MANUAL_APPROVAL`: create a `JoinRequest` in Redis (5-minute TTL), publish
      `JoinRequestCreated`, and return `200` with `status=PENDING` and a
      `requestId`.
- Implement the Redis-backed `JoinRequestRepository` adapter in the meet service
  (ZSET pending queue per meeting, request metadata, device index; atomic Lua
  scripts; TTL) and the meet `RedisConfig` using Spring Data Redis 4.0 / Jackson
  3 serializers.
- Enforce `maxParticipants` capacity under a pessimistic meeting lock to prevent
  over-admission on concurrent joins (`MeetingFull`).
- Add the `JoinCreated` protobuf message (`services/proto`) and its outbox proto
  mapper in the meet service. The outbox relay throws when a mapper is missing,
  so this is required to publish the event.
- **BREAKING (event naming)**: rename the `JoinRequestCreated` event's Kafka
  topic to `meet.join.created` and its CloudEvent type to
  `io.github.smiskinext.meet.join.created.v1` (currently
  `meet.join-request.created`). The event is not yet consumed anywhere, so no
  live consumer breaks.
- Build real-time host notification in the notification service: an SSE
  subscribe endpoint `GET /meetings/{id}/events`, an in-memory emitter registry
  with heartbeat/timeout/cleanup, a Kafka consumer for `meet.join.created` that
  parses the CloudEvent JSON and pushes to the meeting's host emitters, and a
  notification-owned Redis store of pending join requests for replay to hosts
  that subscribe after a request arrives.
- Scale strategy: each notification replica uses its own Kafka consumer group so
  every replica receives every join event and pushes to its locally held
  emitters; the Redis pending store provides cross-instance replay on subscribe.

## Capabilities

### New Capabilities

- `join-meeting`: The meet-service capability to join a meeting via
  `POST /meetings/{id}:join`, covering both admission policies, LiveKit token
  issuance, capacity enforcement, Redis-backed join-request persistence, and
  publishing the `meet.join.created` event.
- `join-request-notification`: The notification-service capability to deliver
  join requests to meeting hosts in real time over SSE, covering host
  subscription, heartbeat/timeout lifecycle, Kafka consumption of
  `meet.join.created`, per-replica fan-out, and Redis-backed replay of pending
  requests on late subscription.

### Modified Capabilities

- `event-driven`: The event catalog gains the `meet.join.created` event (topic
  and CloudEvent type), published via the existing transactional outbox as
  CloudEvents JSON.

## Impact

- **Meet service** (`services/meet`): new `RequestJoinUseCase` +
  `RequestJoinApplicationService`, `RequestJoinCommand`, `RequestJoinResult`;
  `JoinMeetingRequest`/`JoinMeetingResponse` DTOs; new `:join` mapping in
  `MeetingController`; `JoinRequestRedisRepositoryAdapter`; `RedisConfig`;
  `JoinCreatedEventProtoMapper`; edit to `JoinRequestCreatedEvent` (topic/type
  rename). Unit + integration tests (Valkey, Postgres, Kafka testcontainers);
  OpenAPI regeneration.
- **Notification service** (`services/notification`): new
  `SseConnectionManager`, SSE controller, `meet.join.created` Kafka consumer,
  `PendingJoinRequestStore` (Redis), `KafkaConfig`, `SseProperties`,
  `SecurityConfig`, `RedisConfig`; `build.gradle.kts` gains Valkey testcontainer
  for tests. Unit + integration tests (Kafka + Valkey testcontainers).
- **Proto** (`services/proto`): new
  `io/github/smiskinext/event/meet/v1/join_created.proto`.
- **Dependencies**: no new production dependencies; all required starters
  (data-redis, webmvc, kafka, security, proto) come from the `service.base`
  convention plugin. Spring Data Redis 4.0 / Jackson 3 serializer classes
  (`GenericJacksonJsonRedisSerializer`, `tools.jackson.*`) replace the
  deprecated Jackson 2 variants.
- **Out of scope**: host approve/deny endpoints, the approved→client SSE flow,
  `JoinRequestResultStore` adapter, deny/expire flows, cleanup job, meeting
  auto-start on host join, guest (unauthenticated) join, and publishing
  `ParticipantJoined`.
