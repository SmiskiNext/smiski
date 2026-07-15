## 1. Shared outbox write path

- [x] 1.1 Add `OutboxStore.NewOutboxEvent` record (`eventId`, `aggregateId`,
      `aggregateType`, `eventType`, `topic`, `payload`, `occurredAt`) and
      `void append(NewOutboxEvent event)` to
      `shared/.../infrastructure/outbox/OutboxStore.java`
- [x] 1.2 Add shared `EventPublisher` port
      (`publish(shared.domain.PublishableEvent)`) in
      `shared/.../infrastructure/outbox` (or the shared port package consistent
      with layering)
- [x] 1.3 Add shared `OutboxEventPublisher implements EventPublisher`: resolve
      mapper via `OutboxEventProtoMapperRegistry`, encode via
      `CloudEventEncoder`, write via `OutboxStore.append(...)`; never read
      `TenantContext`
- [x] 1.4 Confirm `OutboxAutoConfiguration` still activates on `OutboxStore`
      presence and now also wires the shared publisher

## 2. Meet event contract migration

- [x] 2.1 Retype the 16 meet domain events to implement
      `io.github.smiskinext.shared.domain.PublishableEvent`
- [x] 2.2 Rename `meetingAggregateId` record components to `meetingId` (group A:
      `MeetingScheduledEvent`, `MeetingStartedEvent`,
      `MeetingInvitationsSentEvent`, `MeetingEndedEvent`,
      `MeetingCancelledEvent`, `MeetingSettingsUpdatedEvent`,
      `MeetingInviteTokensInvalidatedEvent`, `InviteeAcceptedEvent`,
      `InviteeDeclinedEvent`)
- [x] 2.3 Remove the manual `meetingAggregateId()` overrides on group B events,
      keeping the `meetingId` field (`ParticipantJoinedEvent`,
      `ParticipantLeftEvent`, `ParticipantKickedEvent`,
      `JoinRequestCreatedEvent`, `JoinRequestApprovedEvent`,
      `JoinRequestDeniedEvent`, `JoinRequestExpiredEvent`)
- [x] 2.4 Add `aggregateId()` returning `meetingId.toString()` to each meet
      event
- [x] 2.5 Update event construction call sites in `Meeting.java` and
      `MeetingInvitee.java` to the renamed component
- [x] 2.6 Delete `meet/.../domain/PublishableEvent.java`

## 3. Meet infrastructure & application wiring

- [x] 3.1 Retype the 3 meet proto mappers to
      `OutboxEventProtoMapper<ConcreteEvent>`; remove raw types,
      `(Object)`/`(Class<?>)` casts, and `@SuppressWarnings`; read `meetingId()`
- [x] 3.2 Implement `append(...)` in meet `OutboxStoreRepositoryAdapter` (build
      `OutboxEventJpaEntity` with `UUID.fromString(event.aggregateId())`)
- [x] 3.3 Delete meet `domain/port/EventPublisher.java` and
      `infrastructure/messaging/OutboxEventPublisher.java`
- [x] 3.4 Update `CreateInstantMeetingApplicationService` to filter/inject the
      shared `EventPublisher` and `shared.domain.PublishableEvent`

## 4. Tenant event contract & infrastructure migration

- [x] 4.1 Retype `TenantInstalledEvent` and `TenantUninstalledEvent` to
      implement `shared.domain.PublishableEvent` directly
- [x] 4.2 Delete `tenant/.../domain/event/PublishableEvent.java`
- [x] 4.3 Add `@TenantId` to tenant `OutboxEventJpaEntity`; remove the
      constructor `tenantId` parameter and its assignment
- [x] 4.4 Implement `append(...)` in tenant `OutboxStoreRepositoryAdapter`
      (store `aggregateId` String directly)
- [x] 4.5 Delete tenant `domain/port/EventPublisher.java` and
      `infrastructure/messaging/OutboxEventPublisher.java` (and the deprecated
      `OutboxRelayScheduler` if present)
- [x] 4.6 Update `RegisterTenantApplicationService` and
      `UninstallTenantApplicationService` to use the shared `EventPublisher` and
      `shared.domain.PublishableEvent`

## 5. Tests (derived from event-driven spec scenarios)

- [x] 5.1 Update existing meet/tenant application + integration tests that
      import the removed `PublishableEvent` types or construct anonymous events
      to use `shared.domain.PublishableEvent`
- [x] 5.2 Test: event enqueued atomically with the state change — one
      unpublished `outbox_event` row with `tenant_id` = request tenant and
      `aggregate_id` = `aggregateId()` (Scenario: Event enqueued atomically)
- [x] 5.3 Test: rolled-back change leaves no `outbox_event` row (Scenario:
      Rolled-back change leaves no event)
- [x] 5.4 Test: `tenant_id` populated by the `@TenantId` discriminator without
      the publisher reading tenant context — assert on tenant service enqueue
      (Scenario: Tenant id populated by the discriminator)
- [x] 5.5 Test: unmapped event type is rejected without a partial write
      (Scenario: Unmapped event type is rejected)
- [x] 5.6 Test: background relay claims and publishes rows across tenants
      despite `@TenantId` on the entity (Scenario: Rows are relayed without a
      bound tenant context / Write path reused)

## 6. Verification

- [x] 6.1 Run `./services/gradlew spotlessApply`
- [x] 6.2 Run `./services/gradlew -p services/ shared build`, `meet build`,
      `tenant build` (unit + ArchUnit + integration) green — confirm layering
      rules pass with the shared `EventPublisher`
- [x] 6.3 Confirm no remaining references to the deleted
      `meet.domain.PublishableEvent` / `tenant.domain.event.PublishableEvent` /
      per-service `EventPublisher` (grep clean)
