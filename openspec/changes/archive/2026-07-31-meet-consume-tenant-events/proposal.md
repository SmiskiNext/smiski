## Why

The `meet` service stores meetings scoped to a tenant (via `tenant_id`), but has
no record of which tenants are active. Without a local tenant projection, `meet`
cannot guard against accepting new meetings from uninstalled tenants, and cannot
react to tenant lifecycle changes (install / uninstall) in a decoupled,
event-driven way.

## What Changes

- New Kafka consumer in `services/meet` that subscribes to
  `tenant.tenant.installed` and upserts a row into the local `tenants` table
  (status = ACTIVE).
- New Kafka consumer in `services/meet` that subscribes to
  `tenant.tenant.uninstalled` and upserts the row (status = UNINSTALLED,
  `uninstalled_at`, `purge_after` populated).
- Both consumers use CloudEvents 1.0 + Protobuf-JSON (matching the outbox
  transport already used by `tenant` service).
- Both consumers share a fixed consumer group (exactly-once delivery per
  replica) with retry-then-DLT error handling (3 attempts, then `<topic>.dlt`).
- New hexagonal layers: domain model (`TenantRecord`, `TenantStatus`), domain
  port (`TenantRepository`), application use cases and services, persistence
  adapter (`TenantJpaEntity`, `TenantJpaRepository`, `TenantRepositoryAdapter`),
  Kafka config and consumer beans.
- No Flyway migration required — the `tenants` table already exists in `meet`'s
  `B1.0.0__baseline.sql`.

## Capabilities

### New Capabilities

- `consume-install-app`: Consume `tenant.tenant.installed` CloudEvent and upsert
  the tenant row into `meet`'s local `tenants` table with status ACTIVE.
- `consume-uninstall-app`: Consume `tenant.tenant.uninstalled` CloudEvent and
  upsert the tenant row with status UNINSTALLED, recording `uninstalled_at` and
  `purge_after`.

### Modified Capabilities

<!-- none -->

## Impact

- **services/meet** — 17 new files across domain, application, infrastructure
  layers; `application.yaml` gains `app.tenant.kafka.*` properties.
- **services/proto** — no changes; `TenantInstalled` and `TenantUninstalled`
  proto messages already compiled and available at
  `io.github.smiskinext.event.tenant.v1`.
- **No API surface changes** — consumers are internal infrastructure with no
  HTTP endpoints.
- **Kafka** — two new consumer groups (`meet-tenant-installed`,
  `meet-tenant-uninstalled`) and two corresponding DLT topics
  (`tenant.tenant.installed.dlt`, `tenant.tenant.uninstalled.dlt`) created on
  first startup.
