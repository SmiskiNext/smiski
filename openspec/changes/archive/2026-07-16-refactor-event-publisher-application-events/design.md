## Context

Write use cases across `tenant` and `meet` currently drain aggregate events by
hand. Each `@Transactional` service method ends with:

```java
for (DomainEvent event : aggregate.getDomainEvents()) {
    if (event instanceof PublishableEvent publishable) {
        eventPublisher.publish(publishable);
    }
}
aggregate.clearDomainEvents();
```

The shared outbox machinery (`OutboxEventPublisher`, `OutboxStore`,
`OutboxRelay`, `KafkaOutboxTransport`) already writes an `outbox_event` row in
the caller's transaction and relays it to Kafka via a scheduled poller. The
`event-driven` spec requires that a publishable event be persisted atomically
with the aggregate change and leave no row on rollback. The duplication is in
the _dispatch_ step, not the outbox itself.

Constraint: the domain layer must stay framework-agnostic (ArchUnit
`CleanArchitectureTest`), so `ApplicationEventPublisher` may appear only in
infrastructure. This change introduces no API endpoint or DB schema change, so
`api-convention` and `db-schema` requirements are unaffected.

## Goals / Non-Goals

**Goals:**

- Remove the repeated event-draining loop from every write use case.
- Preserve the atomic enqueue guarantee: outbox row commits iff the aggregate
  change commits; rollback leaves no row.
- Keep the domain layer free of Spring/messaging types.
- Keep the change opt-in via the existing
  `@ConditionalOnBean(OutboxStore.class)` so outbox-less services are
  unaffected.

**Non-Goals:**

- Changing the relay, transport, CloudEvent encoding, or proto mappers.
- Changing the `outbox_event` schema or any Kafka topic/contract.
- Introducing async event handling or reducing relay latency (a separate
  AFTER_COMMIT relay-trigger optimization is out of scope).

## Decisions

### Decision 1: Port becomes `publishEventsOf(AggregateRoot<?>)`

The port drains the whole aggregate rather than accepting a single event. This
moves the `instanceof PublishableEvent` filter and `clearDomainEvents()` out of
application services into one shared place.

_Alternative considered — keep `publish(PublishableEvent)` and add a helper:_
still forces each service to loop and clear, so duplication persists. Rejected.

### Decision 2: Dispatch through Spring `ApplicationEventPublisher`

`SpringDomainEventPublisher` (infrastructure) forwards each registered domain
event to the framework bus and clears the aggregate. Spring's `ResolvableType`
matching lets a listener subscribe to `PublishableEvent` and ignore
internal-only `DomainEvent`s automatically — no manual filtering.

_Alternative considered — a shared concrete helper that loops and calls
`OutboxEventPublisher` directly:_ simpler, but the application service would
depend on an infrastructure helper and we lose type-based routing for future
internal-only events. The bus keeps domain events one-way and extensible.

### Decision 3: Enqueue in a `BEFORE_COMMIT` transactional listener

`OutboxDomainEventListener` catches `PublishableEvent` with
`@TransactionalEventListener(phase = BEFORE_COMMIT)` and appends via the
existing `OutboxEventPublisher`. `BEFORE_COMMIT` runs inside the active
transaction, so the outbox INSERT commits atomically with the aggregate change
and rolls back with it.

_Alternative considered — `AFTER_COMMIT`:_ runs after the transaction closes, in
a new transaction. The outbox write would no longer be atomic with the aggregate
change, reintroducing the dual-write problem the outbox exists to prevent.
Rejected — this would violate the spec's "rolled-back change leaves no event"
scenario.

_Alternative considered — plain `@EventListener` (synchronous, in-line):_ also
in-transaction and correct, but fires immediately on `publishEvent`, before the
rest of the service method completes. `BEFORE_COMMIT` defers until all business
writes are done, which is safer if events are registered mid-method. Chosen for
the deferral.

### Decision 4: `OutboxEventPublisher` stays as the append delegate

It keeps its `publish(PublishableEvent)` → mapper → CloudEvent →
`outboxStore .append(...)` logic, but is no longer the `EventPublisher` bean.
The listener calls it. Wiring changes live entirely in
`OutboxAutoConfiguration`.

### Flow

```mermaid
sequenceDiagram
    participant Svc as ApplicationService (@Transactional)
    participant Pub as SpringDomainEventPublisher
    participant Bus as ApplicationEventPublisher
    participant Lst as OutboxDomainEventListener
    participant Out as OutboxEventPublisher
    participant DB as outbox_event

    Svc->>Svc: repository.save(aggregate)
    Svc->>Pub: publishEventsOf(aggregate)
    Pub->>Bus: publishEvent(event) for each
    Pub->>Pub: aggregate.clearDomainEvents()
    Note over Svc: method returns; commit begins
    Bus-->>Lst: BEFORE_COMMIT dispatch (PublishableEvent only)
    Lst->>Out: publish(event)
    Out->>DB: INSERT outbox_event (same tx)
    Note over DB: COMMIT — aggregate row + outbox row atomic
```

## Risks / Trade-offs

- **Listener requires an active transaction; without one it silently no-ops** →
  All affected use cases are `@Transactional`. The listener is only reachable
  from an event published inside a transaction; document the requirement and
  rely on integration tests asserting a row is written on commit.
- **Events registered after `publishEventsOf` are never drained** → Convention:
  call `publishEventsOf` as the final step after all state changes, matching the
  current loop's position. Covered by keeping the call at method end.
- **Implicit control flow is harder to trace than an inline loop** → Mitigated
  by a single shared listener and the sequence diagram; the type-based routing
  keeps the wiring discoverable in `OutboxAutoConfiguration`.
- **Internal-only `DomainEvent`s now hit the bus with no listener** → Harmless;
  Spring drops unmatched events. A future handler can subscribe if needed.

## Migration Plan

1. Change the port, add the two shared beans, rewire `OutboxAutoConfiguration`.
2. Replace the loop in the three application services with the single call.
3. Run `tenant` and `meet` `test` + `integrationTest`; the existing rollback and
   outbox-count integration tests validate the atomic guarantee unchanged.
4. Rollback strategy: revert is a pure code revert — no schema or data
   migration, so reverting the commit restores the prior loop with no cleanup.

## Open Questions

- None. The relay-latency optimization (AFTER_COMMIT relay trigger) is
  intentionally deferred to a separate change.
