## Why

Manual-approval join requests reach the meeting host over SSE only after the
shared transactional-outbox relay's scheduled poll picks up the row — a fixed
delay of up to ~5s (`OutboxRelayTrigger`, `fixedDelay = PT5S`). The Kafka →
notification → SSE legs take milliseconds, so the poll interval is the sole
source of the perceived lag. Hosts should see a waiting participant in
near-real-time, not after a multi-second poll window.

## What Changes

- Add a meet-service-local, additive mechanism that publishes SSE-triggering
  domain events to Kafka **near-immediately after the transaction commits**,
  instead of waiting for the scheduled relay poll.
- Introduce a **meet-internal marker interface** (`SseTriggeringEvent`) in the
  meet domain. A publishable event that must reach a host in real time
  implements this marker. Today `JoinRequestCreatedEvent` implements it; future
  SSE events (e.g. approve/deny) implement it without touching the trigger.
- Add an **`AFTER_COMMIT` transactional listener** in meet that, upon commit of
  an `SseTriggeringEvent`, invokes the meet service's own `OutboxRelay.relay()`
  **asynchronously on a virtual thread**, so the `POST /meetings/{id}:join`
  response returns immediately without blocking on Kafka acknowledgment.
- Keep the shared scheduled poll **unchanged** as the durability/retry safety
  net. The immediate kick and the poll claim disjoint rows via the existing
  `FOR UPDATE SKIP LOCKED` semantics, so there is no double-publish; a failed
  kick leaves the row unpublished for the next poll to retry.
- Remove or supersede the empty, misnamed stub change
  `switch-join-notification-to-redis-pubsub` (it pointed at a Redis Pub/Sub
  direction that is not being taken).

Non-goals / explicitly unchanged: the shared outbox module, the poll interval
value, the CloudEvent encoding, the `meet.join.created` topic and event
contract, the notification-service consumer and SSE delivery, other services,
and the join idempotency model (server-generated UUIDv7 request id +
`(meetingId, deviceId)` natural-key dedup).

## Capabilities

### New Capabilities

- `sse-event-fast-relay`: The meet-service capability to relay SSE-triggering
  outbox events to Kafka near-immediately after their transaction commits — via
  a marker-selected `AFTER_COMMIT` listener that asynchronously triggers the
  service-local outbox relay — while the shared scheduled poll remains the
  fallback that guarantees eventual delivery and retry.

### Modified Capabilities

<!-- None. The shared `event-driven` relay contract (scheduled poll, SKIP LOCKED batching, retry/failure recording, at-least-once delivery) remains literally true and unchanged; this change only adds a service-local trigger that runs the same relay sooner. No existing spec requirement changes. -->

## Impact

- **Meet service** (`services/meet`), additive only:
    - `meet/domain/event/SseTriggeringEvent` — new framework-agnostic marker
      interface; `JoinRequestCreatedEvent` implements it.
    - `meet/infrastructure/messaging/SseRelayKickListener` — new
      `@TransactionalEventListener(phase = AFTER_COMMIT)` listening for
      `SseTriggeringEvent`, dispatching `OutboxRelay.relay()` on a
      virtual-thread executor.
    - `meet/infrastructure/config` — a small async/executor configuration if the
      existing `applicationTaskExecutor` (virtual threads already enabled via
      `spring.threads.virtual.enabled=true`) is not directly reusable.
- **Shared** (`services/shared`): no change. `OutboxRelay` is already a
  per-service bean (`@ConditionalOnBean(OutboxStore.class)`), so the meet kick
  only touches meet's `outbox_event`.
- **Notification service**, **proto**, **other services**, **database schema**,
  **APIs/OpenAPI**: no change.
- **Behavioral impact**: host receives the join-created SSE notification within
  milliseconds of request creation instead of up to ~5s; delivery guarantees,
  ordering, and idempotency are preserved by the unchanged relay and consumer.
- **Housekeeping**: delete/supersede the empty
  `openspec/changes/switch-join-notification-to-redis-pubsub` stub.
