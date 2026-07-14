## 1. Shared: configuration & contracts

- [x] 1.1 Add `io.cloudevents:cloudevents-json-jackson` to the shared module's
      dependencies (via `gradle/libs.versions.toml` +
      `services/shared/build.gradle.kts`) if not already present
- [x] 1.2 Create `OutboxProperties` in shared (`infrastructure/outbox`) as a
      `@Validated @ConfigurationProperties(prefix = "smiski.outbox")` record:
      `relay.enabled` (default true), `relay.fixedDelay` (Duration),
      `relay.batchSize` (min 1), `cloudevent.source` (URI/string), `transport`
      (default `kafka`)
- [x] 1.3 Define `OutboxTransport` port in shared:
      `void send(String topic, String key, io.cloudevents.CloudEvent event)`
- [x] 1.4 Define `OutboxStore` port in shared: claim a bounded batch of
      unpublished rows (returns transport-neutral records with id, tenantId,
      aggregateId, topic, payload), mark-published, and record-failure
      operations
- [x] 1.5 Define `OutboxEventProtoMapper<E extends PublishableEvent>` in shared
      with `Class<E> eventType()`, `String dataSchema()`,
      `com.google.protobuf.Message toProto(E event)`; move/define the shared
      `PublishableEvent` contract (eventId, aggregateId, aggregateType,
      eventType, topic, occurredAt) so services reuse it

## 2. Shared: encoding, transport, relay orchestration

- [x] 2.1 Implement `OutboxEventProtoMapperRegistry` that resolves a mapper by
      `event.getClass()` and fails clearly when none is registered
- [x] 2.2 Implement `CloudEventEncoder` in shared: build a CloudEvents 1.0 event
      (proto-JSON `data`, `datacontenttype=application/json`, versioned `type`,
      `source` from properties, URI `dataschema`, unique `id`) and encode/decode
      to/from TEXT using the CloudEvents Jackson data format
- [x] 2.3 Implement `KafkaOutboxTransport` in shared, guarded by
      `@ConditionalOnProperty(prefix="smiski.outbox", name="transport", havingValue="kafka", matchIfMissing=true)`,
      depending on `KafkaTemplate<String, CloudEvent>`
- [x] 2.4 Implement `OutboxRelay` in shared with three boundaries: (a) short
      `@Transactional` claim via `OutboxStore`, (b) transaction-free publish
      that fires async transport sends and awaits futures, (c) mark-published in
      a short tx and record-failure in `Propagation.REQUIRES_NEW`
- [x] 2.5 Implement the
      `@Scheduled(fixedDelayString = "#{outboxProperties...}")` trigger for
      `OutboxRelay`, gated by `relay.enabled`
- [x] 2.6 Create `OutboxAutoConfiguration` in shared registering the above
      beans, with relay/transport activation
      `@ConditionalOnBean(OutboxStore.class)`; register it in
      `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 3. Tenant: adopt shared outbox

- [x] 3.1 Remove `@TenantId` from `OutboxEventJpaEntity` (keep the `tenant_id`
      column, `(tenant_id, id)` PK, and all other columns unchanged); ensure
      `tenant_id` is populated explicitly on construction
- [x] 3.2 Add a batch-claim query to `OutboxEventJpaRepository` using
      `FOR UPDATE SKIP LOCKED` semantics (`@Lock(PESSIMISTIC_WRITE)` +
      `jakarta.persistence.lock.timeout = -2`, or native
      `... FOR UPDATE SKIP LOCKED`) with
      `WHERE published_at IS NULL ORDER BY created_at LIMIT :batchSize`
- [x] 3.3 Implement a tenant `OutboxStoreAdapter` (`@Repository`) over
      `OutboxEventJpaRepository` implementing the shared `OutboxStore` port
      (claim/mark/record-failure); mark-published and record-failure via
      short/`REQUIRES_NEW` transactions
- [x] 3.4 Replace `OutboxEventPublisher` internals to set `tenant_id` from
      `TenantContext`, resolve the proto mapper via the shared registry, and
      encode via `CloudEventEncoder` (remove `instanceof TenantInstalledEvent`
      and the hardcoded `dataschema`/hand-built JSON)
- [x] 3.5 Convert `TenantEventProtoMapper` into a registered
      `OutboxEventProtoMapper<TenantInstalledEvent>` bean exposing
      `dataSchema()` and `toProto()`
- [x] 3.6 Delete `OutboxRelayScheduler` (superseded by shared `OutboxRelay`) and
      wire the tenant service to the shared relay
- [x] 3.7 Remove the duplicated producer settings in `KafkaProducerConfig`; keep
      only the `KafkaTemplate<String, CloudEvent>` bean if not provided by
      auto-config, and rely on `spring.kafka.producer.*` in `application.yaml`
      as the single source
- [x] 3.8 Add `smiski.outbox.*` defaults to `application.yaml` (source,
      batch-size, fixed-delay, transport=kafka)
- [x] 3.9 Confirm `RegisterTenantApplicationService` publish loop still enqueues
      within the upsert transaction with the refactored publisher (no behavior
      change to the enqueue boundary)

## 4. Tests — enqueue (event-driven: Transactional outbox enqueue)

- [x] 4.1 Regression test: event enqueued atomically — after a committed
      install, exactly one `outbox_event` row exists with `published_at` null
      and `tenant_id`/`aggregate_id` equal to the cloudId, and the tenant row is
      present
- [x] 4.2 Rolled-back upsert leaves no `outbox_event` row for that installation
- [x] 4.3 Domain event mapped to proto at the boundary via the resolved mapper;
      assert the domain event type imports no proto/Kafka types (extend/verify
      ArchUnit)
- [x] 4.4 Unmapped event type is rejected and writes no `outbox_event` row

## 5. Tests — relay (event-driven: Scheduled relay across tenants)

- [x] 5.1 Regression test for the tenant-filter bug: relay runs on a thread with
      NO bound tenant (do not call `TenantContext.setCurrentTenant`) and still
      selects + publishes a row stored under a real tenant, then sets
      `published_at`
- [x] 5.2 Concurrent replicas do not double-publish: two relay invocations
      against the same unpublished rows each claim disjoint rows via
      `FOR UPDATE SKIP LOCKED`
- [x] 5.3 Already-published rows (with `published_at` set) are not selected or
      resent
- [x] 5.4 Publish failure increments `retry_count`, records `last_error` in an
      independent transaction, leaves `published_at` null, and the row remains
      eligible next run
- [x] 5.5 Aggregate id (cloudId) is used as the transport message key
- [x] 5.6 Update/replace the existing `OutboxRelaySchedulerIntegrationTest` and
      `OutboxRelayFailureIntegrationTest` so they no longer set the tenant
      context on the relay thread (remove the false-positive setup)

## 6. Tests — encoding & configuration (event-driven: CloudEvent payload / Configurable transport)

- [x] 6.1 Payload is a proto-JSON CloudEvent in TEXT with
      `datacontenttype=application/json` and a versioned stable `type` and
      unique `id`
- [x] 6.2 Malformed stored payload: relay does not publish or mark published;
      records failure and leaves the row retryable
- [x] 6.3 Transport is selected by configuration (Kafka default) and the relay
      depends only on the transport abstraction
- [x] 6.4 Relay stays inactive when no `OutboxStore` bean is present (shared
      auto-config gating)

## 7. Verification

- [x] 7.1 Run `./services/gradlew -p services/ tenant test` (fast: unit +
      ArchUnit) and fix failures
- [x] 7.2 Run `./services/gradlew -p services/ tenant integrationTest`
      (Testcontainers) and fix failures
- [x] 7.3 Run `./services/gradlew -p services/ shared test` and
      `./services/gradlew spotlessApply`
- [x] 7.4 Run `openspec validate refactor-shared-outbox-relay --strict` and
      reconcile any spec drift
