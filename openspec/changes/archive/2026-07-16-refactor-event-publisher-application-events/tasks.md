## 1. Shared port and dispatch

- [x] 1.1 Change `shared.domain.EventPublisher` from `publish(PublishableEvent)`
      to `publishEventsOf(AggregateRoot<?>)`
- [x] 1.2 Add `shared.infrastructure.event.SpringDomainEventPublisher`
      implementing `EventPublisher`: forward each `aggregate.getDomainEvents()`
      to `ApplicationEventPublisher`, then `clearDomainEvents()`
- [x] 1.3 Add `shared.infrastructure.outbox.OutboxDomainEventListener` with
      `@TransactionalEventListener(phase = BEFORE_COMMIT)` on `PublishableEvent`
      that calls `OutboxEventPublisher.publish(event)`

## 2. Shared wiring

- [x] 2.1 In `OutboxAutoConfiguration`, keep `OutboxEventPublisher` as a plain
      bean (append delegate), and remove its role as the `EventPublisher` bean
- [x] 2.2 In `OutboxAutoConfiguration`, add `EventPublisher` bean backed by
      `SpringDomainEventPublisher(ApplicationEventPublisher)`
- [x] 2.3 In `OutboxAutoConfiguration`, add `OutboxDomainEventListener` bean,
      keeping all new beans under the existing
      `@ConditionalOnBean(OutboxStore.class)` gate

## 3. Application service call sites

- [x] 3.1 Replace the event loop in `tenant` `RegisterTenantApplicationService`
      with `eventPublisher.publishEventsOf(tenant)` and drop
      `DomainEvent`/`PublishableEvent` imports
- [x] 3.2 Replace the event loop in `tenant` `UninstallTenantApplicationService`
      with `eventPublisher.publishEventsOf(tenant)` and drop unused imports
- [x] 3.3 Replace the event loop in `meet`
      `CreateInstantMeetingApplicationService` with
      `eventPublisher.publishEventsOf(meeting)` and drop unused imports

## 4. Tests

- [x] 4.1 Unit test `SpringDomainEventPublisher`: given an aggregate with
      registered events, `publishEventsOf` forwards each to
      `ApplicationEventPublisher` and clears the aggregate (scenario: Aggregate
      events drained by a single publisher call)
- [x] 4.2 Unit test `OutboxDomainEventListener`: a `PublishableEvent` triggers
      `OutboxEventPublisher.publish`; a non-publishable `DomainEvent` does not
      (scenarios: Aggregate events drained; Non-publishable domain event is not
      enqueued)
- [x] 4.3 Update `shared` `OutboxAutoConfigurationTest`: assert
      `EventPublisher`, `SpringDomainEventPublisher`, and
      `OutboxDomainEventListener` beans are absent when no `OutboxStore` is
      present (scenario: Relay stays inactive when no storage adapter is
      present)
- [x] 4.4 Verify existing integration coverage still passes for atomic enqueue
      and rollback: `meet` `MeetingControllerIntegrationTest`,
      `MeetingCreationRollbackIntegrationTest`, and `tenant`
      `TenantRepositoryAdapterIntegrationTest` (scenarios: Event enqueued
      atomically with the state change; Rolled-back change leaves no event) —
      update only if the port change breaks compilation, do not duplicate
      assertions

## 5. Verification

- [x] 5.1 Run `./services/gradlew -p services/tenant test integrationTest`
- [x] 5.2 Run `./services/gradlew -p services/meet test integrationTest`
- [x] 5.3 Run `./services/gradlew spotlessApply` and confirm ArchUnit
      `CleanArchitectureTest` passes for both services (domain remains free of
      Spring imports)
