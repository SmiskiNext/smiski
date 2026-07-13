## Why

The tenant service's transactional outbox relay is functionally broken in
production and non-reusable. The `outbox_event` entity is annotated with
Hibernate `@TenantId`, so every query is silently filtered by the current
tenant; the scheduled relay runs on a background thread with no request-bound
tenant, resolves to the fallback `system` tenant, and therefore never selects
any real tenant's rows — no event is ever delivered to Kafka. Integration tests
pass only because they set the tenant context manually on the same thread, a
false positive. The publishing design is also hardwired to Kafka and to a single
event type (`instanceof TenantInstalledEvent`, a hardcoded `dataschema`),
duplicated inside the service, and configured through scattered `@Value`
injections, so it cannot be reused by other services or extended to new event
types or transports.

## What Changes

- **BREAKING (internal)** Remove the Hibernate `@TenantId` discriminator from
  the outbox entity so it behaves as a global infrastructure table; the relay
  reads across all tenants and the publisher writes `tenant_id` explicitly from
  the request context. The `(tenant_id, id)` primary key and DDL are unchanged.
- Make the relay claim work with `SELECT ... FOR UPDATE SKIP LOCKED` and a
  bounded `LIMIT` batch so multiple replicas never double-publish and never load
  the whole backlog.
- Restructure the relay into three boundaries: a short read transaction, a
  transaction-free publish phase (async sends + flush/await), and short write
  transactions to mark published rows and record failures (`REQUIRES_NEW`) — no
  DB connection is held open across blocking Kafka calls.
- Replace the `instanceof`/hardcoded-`dataschema` publisher with a
  per-event-type mapper registry so additional event types are added by
  registering a bean, not by editing the publisher. CloudEvent encode/decode
  uses the CloudEvents Jackson data format instead of hand-built/parsed JSON.
- Introduce an `OutboxTransport` port with a Kafka implementation selected by
  configuration, so a future transport (e.g. AWS EventBridge/SNS) can be added
  without touching the relay. Kafka/MSK remains config-only.
- Externalise all tuning into a single validated `@ConfigurationProperties`
  class (`smiski.outbox.*`) and remove the duplicated hand-built Kafka producer
  config that shadows the `application.yaml` producer settings.
- Move the generic outbox machinery (properties, transport, CloudEvent encoder,
  per-event mapper registry, relay orchestration, and a store port) into the
  `shared` module as auto-configuration; the tenant service keeps only its JPA
  entity/repository adapter and its event→proto mapper bean.

## Capabilities

### New Capabilities

<!-- None. This change hardens and refactors existing behavior; it adds no new
     product capability. -->

### Modified Capabilities

- `event-driven`: The transactional outbox relay requirements are refined — the
  relay reads outbox rows across all tenants (not filtered by the request
  tenant), claims batches with `FOR UPDATE SKIP LOCKED`, publishes without
  holding a database transaction, maps each domain event to its proto contract
  through a per-event-type registry (removing the single-event hardcoding), and
  publishes through a configurable transport abstraction rather than a
  Kafka-only path.

## Impact

- **Code (tenant service)**: `infrastructure/messaging/OutboxEventPublisher`,
  `OutboxRelayScheduler`, `TenantEventProtoMapper`,
  `infrastructure/persistence/OutboxEventJpaEntity`, `OutboxEventJpaRepository`,
  `infrastructure/config/KafkaProducerConfig`,
  `application/service/RegisterTenantApplicationService` (publish loop).
- **Code (shared module)**: new generic outbox package + auto-configuration
  (properties, transport port + Kafka adapter, CloudEvent encoder, event mapper
  registry, relay, store port).
- **Config**: new `smiski.outbox.*` properties; removal of duplicated Kafka
  producer bean; `application.yaml` producer settings become the single source.
- **Schema**: none — DDL, `(tenant_id, id)` PK, and the unpublished partial
  index are unchanged (`db-schema` transactional-outbox requirement still
  holds).
- **Tests**: relay integration tests must exercise the relay on a background
  thread without a bound tenant to prevent the previous false positive; new unit
  tests for the mapper registry and CloudEvent encoder.
- **Dependencies**: add `io.cloudevents:cloudevents-json-jackson` (CloudEvents
  Jackson data format) if not already present.
- **Reuse**: `meet`/`record` can adopt the shared outbox by providing only their
  entity/repository adapter and event→proto mapper beans.
