# Implementation Tasks

## 1. Marker + event opt-in (meet domain)

- [x] 1.1 Add `meet/domain/event/SseTriggeringEvent` marker interface
      (framework-agnostic: no Spring/Kafka/JPA imports)
- [x] 1.2 Make `JoinRequestCreatedEvent` implement `SseTriggeringEvent` ←
      (verify: marker is in domain/event with no framework imports;
      JoinRequestCreatedEvent still implements shared PublishableEvent and
      compiles)

## 2. Async trigger (meet infrastructure)

- [x] 2.1 Ensure a virtual-thread `Executor` is available: reuse Spring Boot's
      auto-configured `applicationTaskExecutor`, or add a minimal
      `@EnableAsync` + executor bean in `meet/infrastructure/config` if not
      directly injectable
- [x] 2.2 Add `SseRelayKickListener` in `meet/infrastructure/messaging` with
      `@TransactionalEventListener(phase = AFTER_COMMIT)` for
      `SseTriggeringEvent`, dispatching `OutboxRelay.relay()` on the
      virtual-thread executor; guard so a relay error/rejection is logged and
      swallowed (fallback poll handles it) ← (verify: listener fires only after
      commit, runs off the request thread, invokes meet's per-service
      OutboxRelay; ArchUnit passes with listener in infrastructure/messaging)

## 3. Tests (from spec scenarios)

- [x] 3.1 Unit (`SseRelayKickListener`): dispatches relay asynchronously for a
      marked event; does not invoke relay for a non-marked publishable event;
      relay exception is swallowed (no propagation to caller) — mock
      `OutboxRelay` and executor (run-synchronously test executor) ← (verify:
      covers "Unmarked event is not relayed immediately" and "Non-blocking
      trigger" scenarios)
- [x] 3.2 Integration (`:join` on Postgres+Valkey+Kafka testcontainers):
      creating a MANUAL_APPROVAL pending request results in `meet.join.created`
      on Kafka within a short bound well under the poll interval (e.g. ≤ 2s),
      asserting immediate delivery rather than poll-driven ← (verify: covers
      "Marked event published without waiting for the poll"; must not rely on
      the PT5S poll — assert delivery faster than the poll interval)
- [x] 3.3 Integration: rolled-back join transaction produces no `outbox_event`
      row and triggers no immediate relay ← (verify: covers "Immediate relay
      runs only after commit")
- [x] 3.4 Integration: exactly-once publishing preserved — a marked row
      published by the immediate relay is not resent by a subsequent scheduled
      poll (published_at set); concurrent kick+poll do not double-publish ←
      (verify: covers "double-publish" and "published exactly once across both
      paths" scenarios; leans on existing FOR UPDATE SKIP LOCKED)

## 4. Verification gate

- [x] 4.1 `./services/gradlew spotlessApply`
- [x] 4.2 `./services/gradlew -p services/meet build` (test + integrationTest +
      ArchUnit) — GREEN ← (verify: all builds green, no ArchUnit violations,
      immediate-delivery integration test passes under the sub-poll-interval
      bound)

## 5. Housekeeping

- [x] 5.1 Remove the empty, misnamed stub change directory
      `openspec/changes/switch-join-notification-to-redis-pubsub` ← (verify:
      directory no longer present; `openspec list` no longer shows it)
