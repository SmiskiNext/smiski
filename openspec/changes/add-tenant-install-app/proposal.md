## Why

When a user installs the Smiski Forge app on a Jira site, Forge emits an
`avi:forge:installed:app` lifecycle event. The backend currently has no way to
receive that event: the `tenant` service is scaffolding only, so installations
are never recorded and downstream services have no tenant to project. This
change delivers the first end-to-end vertical slice — an endpoint that persists
the installation and emits a durable lifecycle event — establishing the
publisher half of the tenant projection contract other services depend on.

## What Changes

- Add `POST /tenants` to the `tenant` service. It accepts the Forge install
  event payload as the request body and reads the tenant identifier (Jira
  cloudId) from the tenant context (`X-Tenant-ID` header), never from the body.
- Persist (upsert) the installation into the existing `tenants` table. A first
  install creates the row (`201 Created`); a redelivery or reinstall updates it
  (new `installation_id`, reactivated `status`, refreshed timestamps) and
  returns `200 OK`. The response body is the tenant representation.
- Emit a `TenantInstalled` domain event through the transactional outbox
  (`outbox_event`) in the same transaction as the upsert, then relay it to Kafka
  as a CloudEvent via a scheduled poller.
- Define the event contract as a shared Protocol Buffers message
  (`event/tenant/v1/tenant_installed.proto`) so the publisher and future
  consumers share one schema. The proto payload is carried as proto-JSON inside
  the CloudEvent `data` (`application/json`), stored in the
  `outbox_event.payload` TEXT column.
- Introduce the shared application-layer contracts (`UseCase<I, O, E>`,
  `Command`, `Query`) in `services/shared`, which do not yet exist, to set the
  inbound-port pattern for every service.

## Capabilities

### New Capabilities

- `install-app`: Receiving a Forge app-installation lifecycle event and
  recording the tenant installation — the HTTP contract, tenant-context
  resolution, idempotent upsert behavior, and the tenant representation
  returned.
- `event-driven`: Publishing tenant lifecycle events through a transactional
  outbox and relaying them to Kafka as CloudEvents whose payload schema is a
  shared Protocol Buffers contract reused by publisher and consumers.

### Modified Capabilities

<!-- No existing capability's requirements change. db-schema already specifies
     the tenants table, outbox, and projection behavior; this change implements
     against those requirements without altering them. -->

## Impact

- **New service code** (`services/tenant`): domain (`Tenant` aggregate,
  `TenantStatus`, `EnvironmentType`, value objects, `TenantInstalledEvent`,
  `PublishableEvent`, ports, `TenantError`/`TenantErrorCode`), application
  (`RegisterTenantUseCase`, command, response, mapper, service), infrastructure
  (JPA entity + repository adapter + persistence mapper, outbox entity +
  repository, outbox event publisher, Kafka CloudEvent relay scheduler, Kafka
  producer + message-bundle config), presentation (request DTO + controller),
  message bundles (`messages/tenant.properties`, `_vi`).
- **Shared library** (`services/shared`): new `UseCase`, `Command`, `Query`
  contracts in `application/`.
- **Proto module** (`services/proto`): new `tenant_installed.proto` under
  `io/github/smiskinext/event/tenant/v1/`.
- **Build**: `tenant` build gains `protobuf-java-util` (for `JsonFormat`); proto
  already on the classpath via the `service.base` convention plugin.
- **Config**: enable scheduling; Kafka producer already configured with
  `CloudEventSerializer` in `application.yaml`.
- **Tests**: unit (aggregate, service) and integration (repository adapter,
  outbox publisher, Kafka relay via Kafka + Postgres Testcontainers, controller
  problem+json, OpenAPI generation).
- **Out of scope**: the `avi:forge:upgraded:app` and pre-uninstall lifecycle
  events, the Forge app wiring (`app/src/index.ts`, manifest) that forwards the
  event, and the downstream consumer projection.
