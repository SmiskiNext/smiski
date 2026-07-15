## MODIFIED Requirements

### Requirement: Transactional outbox enqueue

A domain lifecycle event SHALL be persisted to the `outbox_event` table within
the same database transaction as the aggregate state change, satisfying the
db-schema transactional-outbox requirement. The outbox row SHALL record the
`tenant_id`, `aggregate_id`, `aggregate_type`, `event_type`, `topic`, and a
`payload`. The `tenant_id` SHALL be populated by the Hibernate tenant
discriminator (`@TenantId`) on the outbox entity from the current request
tenant, so that the publisher does not read or pass the tenant explicitly. The
`outbox_event` table SHALL be treated as a global infrastructure table for
relaying: the background relay SHALL select rows across all tenants and SHALL
NOT be constrained by the request-bound tenant discriminator, even though each
row retains its `tenant_id` column and `(tenant_id, id)` primary key. The event
SHALL be durable if and only if the aggregate state change commits.

Every publishable domain event across all services SHALL implement one shared
event contract (`io.github.smiskinext.shared.domain.PublishableEvent`) whose
aggregate identity is exposed as a String `aggregateId()`; services SHALL NOT
define their own `PublishableEvent` interface. The domain SHALL remain free of
Protocol Buffers and messaging types; mapping the domain event to the proto
contract and to the stored payload SHALL happen at the infrastructure boundary
through a per-event-type mapper resolved by the domain event's runtime type.
Each mapper SHALL be typed to its concrete event type so that mapping is
compile-time-checked, and adding a new publishable event type SHALL require
registering a new mapper rather than modifying the publisher.

#### Scenario: Event enqueued atomically with the state change

- **WHEN** an aggregate change is recorded and its transaction commits
- **THEN** exactly one corresponding row exists in `outbox_event` with
  `published_at` null, `tenant_id` set to the current request tenant, and
  `aggregate_id` equal to the event's `aggregateId()`

#### Scenario: Rolled-back change leaves no event

- **WHEN** the aggregate transaction is rolled back before commit
- **THEN** no `outbox_event` row for that change exists

#### Scenario: Tenant id populated by the discriminator, not the publisher

- **WHEN** the shared publisher enqueues an event within a request that has a
  bound tenant
- **THEN** the stored row's `tenant_id` matches the request tenant even though
  the publisher never reads the tenant context or receives it as an argument

#### Scenario: Single shared event contract across services

- **WHEN** a service registers a publishable domain event
- **THEN** that event implements `shared.domain.PublishableEvent` directly, no
  service-specific `PublishableEvent` interface exists, and the shared publisher
  accepts it without a service-specific type

#### Scenario: Domain event mapped to proto at the boundary by a typed mapper

- **WHEN** the application registers a publishable domain event
- **THEN** the infrastructure layer resolves the concrete-typed mapper for that
  event type, maps it to its proto message, and produces the stored payload, and
  the domain event type itself imports no Protocol Buffers or Kafka types

#### Scenario: Unmapped event type is rejected without partial write

- **WHEN** a publishable event has no registered proto mapper
- **THEN** enqueue fails and no `outbox_event` row is written for that event

### Requirement: Configurable, reusable outbox transport

The outbox publish path SHALL be provided by a transport abstraction so that the
concrete messaging technology is selected by configuration rather than
referenced directly by the relay. A Kafka transport SHALL be the default. All
relay tuning — including whether the relay is enabled, its polling delay, its
batch size, the CloudEvent source, and the selected transport — SHALL be
supplied through a single externalized, validated configuration properties type
rather than through scattered individual property injections. The generic outbox
machinery SHALL cover both the read/relay path and the write/enqueue path: a
storage port exposing an append operation for writing rows and
claim/mark/failure operations for relaying, a single reusable event publisher
over that storage port, CloudEvent encoding, a per-event mapper registry, the
transport port and Kafka adapter, and relay orchestration. A service SHALL be
able to publish and relay domain events by providing only a storage adapter and
its event mappers, without duplicating the publisher or relay logic.

#### Scenario: Transport is selected by configuration

- **WHEN** the outbox transport is configured to the Kafka transport (or left at
  its default)
- **THEN** the relay publishes through the Kafka transport, and the relay code
  depends only on the transport abstraction, not on a specific messaging client

#### Scenario: Relay tuning comes from externalized configuration

- **WHEN** the relay polling delay, batch size, CloudEvent source, or enabled
  flag is changed in external configuration
- **THEN** the relay behavior reflects the configured values without code
  changes

#### Scenario: Write path is reused across services

- **WHEN** a service provides only an outbox storage adapter and its event
  mappers
- **THEN** it enqueues events through the single shared publisher over the
  shared storage port, without declaring its own event-publisher port or write
  adapter

#### Scenario: Relay stays inactive when no storage adapter is present

- **WHEN** a service includes the shared outbox machinery but provides no outbox
  storage adapter
- **THEN** the relay does not activate and the service starts normally
