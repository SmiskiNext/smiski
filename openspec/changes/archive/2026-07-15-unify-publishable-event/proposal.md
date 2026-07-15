## Why

Each service defines its own `PublishableEvent` interface and its own
`EventPublisher` port + `OutboxEventPublisher` write adapter, even though the
shared library already owns the reusable relay, encoder, and mapper registry.
The tenant marker adds nothing; the meet variant adds `meetingAggregateId()` and
forced the mappers to abandon compile-time type safety (raw types + casts +
`@SuppressWarnings`). The result is duplicated write-path code and weaker type
checking than the tenant service originally had.

## What Changes

- **BREAKING** Remove `io.github.smiskinext.meet.domain.PublishableEvent` and
  `io.github.smiskinext.tenant.domain.event.PublishableEvent`. All domain events
  in both services implement
  `io.github.smiskinext.shared.domain.PublishableEvent` directly as the single
  event contract.
- **BREAKING** Remove `meetingAggregateId()` from the meet event contract. Each
  meet event keeps a UUID field named `meetingId` and implements `aggregateId()`
  (returns `meetingId.toString()`); mappers read the concrete `meetingId()`
  accessor.
- **BREAKING** Remove the per-service `EventPublisher` ports
  (`meet.domain.port.EventPublisher`, `tenant.domain.port.EventPublisher`) and
  the two `OutboxEventPublisher` write adapters. Provide a single shared
  `EventPublisher` port and shared `OutboxEventPublisher` typed to
  `shared.domain.PublishableEvent`.
- Add a write operation `append(...)` to the shared `OutboxStore` port so the
  shared publisher writes rows through the storage abstraction instead of a
  service-specific JPA repository. Each service keeps only its `OutboxStore`
  adapter and its proto mappers.
- Restore compile-time type safety in the meet proto mappers: each implements
  `OutboxEventProtoMapper<ConcreteEvent>`; remove raw types, `(Object)` casts,
  and `@SuppressWarnings`.
- Standardize outbox `tenant_id` population on Hibernate `@TenantId` in both
  services so the shared publisher stays tenant-agnostic; remove tenant's manual
  `TenantContext.getCurrentTenant()` write and its constructor `tenantId`
  parameter.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `event-driven`: The "Transactional outbox enqueue" and "Configurable, reusable
  outbox transport" requirements change — the reusable machinery now includes
  the write path (a single shared `EventPublisher`/publisher over an
  `OutboxStore` storage port with an `append` operation), a single shared
  `PublishableEvent` contract used by every service, type-safe per-event
  mappers, and `tenant_id` populated via the Hibernate tenant discriminator on
  the outbox entity.

## Impact

- **Shared library**: `OutboxStore` gains `append(...)`; new shared
  `EventPublisher` port + `OutboxEventPublisher`; touches `outbox` package.
- **meet service**: delete `domain.PublishableEvent`,
  `domain.port.EventPublisher`, `infrastructure.messaging.OutboxEventPublisher`;
  update 16 domain events (rename `meetingAggregateId` component → `meetingId`,
  add `aggregateId()`); update 3 proto mappers to typed generics; update
  `CreateInstantMeetingApplicationService` event-filter import;
  `OutboxStoreRepositoryAdapter` implements `append` (`UUID.fromString`).
- **tenant service**: delete `domain.event.PublishableEvent`,
  `domain.port.EventPublisher`, `infrastructure.messaging.OutboxEventPublisher`;
  update `TenantInstalledEvent`/`TenantUninstalledEvent`; add `@TenantId` to the
  tenant `OutboxEventJpaEntity` and drop the constructor `tenantId` parameter;
  update `RegisterTenant`/`UninstallTenant` app services event-filter import;
  `OutboxStoreRepositoryAdapter` implements `append`.
- **Tests**: meet + tenant application/integration tests that reference the
  removed `PublishableEvent` types or construct anonymous events must switch to
  `shared.domain.PublishableEvent`.
- **ArchUnit**: application layer now depends on the shared `EventPublisher`
  port; layering rules must still pass.
- **No schema migration**: `outbox_event` tables and columns are unchanged (only
  the tenant `@TenantId` mapping annotation is added).
- **No proto changes**: existing `event/meet/v1` and `event/tenant/v1` messages
  are untouched.
