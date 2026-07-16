## Why

Every application service that mutates an aggregate currently repeats the same
block — iterate `aggregate.getDomainEvents()`, filter
`instanceof PublishableEvent`, call `eventPublisher.publish(...)`, then
`clearDomainEvents()` — in `RegisterTenantApplicationService`,
`UninstallTenantApplicationService`, and
`CreateInstantMeetingApplicationService`. This couples every write use case to
the outbox write mechanics, duplicates event-filtering logic, and means each new
use case must remember the ritual or silently drop events. Centralizing dispatch
removes the duplication while preserving the transactional-outbox atomicity
guarantee.

## What Changes

- Change the shared `EventPublisher` port from `publish(PublishableEvent)` to
  `publishEventsOf(AggregateRoot<?>)`, so a use case drains an aggregate's
  events in one call instead of hand-writing a loop. **BREAKING** for the port
  contract (internal to backend; no external API).
- Add a shared `SpringDomainEventPublisher` (infrastructure) implementing
  `EventPublisher` that forwards each registered domain event to Spring's
  `ApplicationEventPublisher` and clears the aggregate.
- Add a shared `OutboxDomainEventListener` that catches `PublishableEvent` via
  `@TransactionalEventListener(phase = BEFORE_COMMIT)` and appends it to the
  outbox through the existing `OutboxEventPublisher` delegate — keeping the
  enqueue inside the aggregate's transaction.
- Repurpose `OutboxEventPublisher`: it stays the single append-to-outbox
  delegate but is no longer wired as the `EventPublisher` bean.
- Wire the new beans in `OutboxAutoConfiguration`, gated by the existing
  `@ConditionalOnBean(OutboxStore.class)` so services without an outbox store
  are unaffected.
- Replace the manual event loop in the three application services with a single
  `eventPublisher.publishEventsOf(aggregate)` call and drop their direct
  `DomainEvent`/`PublishableEvent` imports.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `event-driven`: The "Transactional outbox enqueue" and "Configurable, reusable
  outbox transport" requirements change to specify that publishable domain
  events registered on an aggregate are dispatched through the framework
  application-event publisher and drained into the outbox by a shared pre-commit
  transactional listener, so application services no longer iterate events
  explicitly while the atomic-enqueue and rollback guarantees remain.

## Impact

- `services/shared/src/main/java/io/github/smiskinext/shared/domain/EventPublisher.java`
  (port signature change).
- `services/shared/src/main/java/io/github/smiskinext/shared/infrastructure/` —
  new `event/SpringDomainEventPublisher.java`,
  `outbox/OutboxDomainEventListener.java`; modified
  `outbox/OutboxAutoConfiguration.java`, `outbox/OutboxEventPublisher.java`.
- `services/tenant` — `RegisterTenantApplicationService`,
  `UninstallTenantApplicationService` (loop → single call).
- `services/meet` — `CreateInstantMeetingApplicationService` (loop → single
  call).
- Behavior contracts in `openspec/specs/event-driven/spec.md` (atomic enqueue,
  rollback leaves no row) are preserved; only the dispatch mechanism changes.
- No database schema, proto contract, or external API changes.
