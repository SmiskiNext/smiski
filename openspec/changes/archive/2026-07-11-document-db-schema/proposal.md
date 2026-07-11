## Why

The backend is a set of database-per-service microservices (`tenant`, `meet`,
`record`), each owning its own Postgres schema under `db/migration/`. The
cross-service persistence contract — multi-tenant hash partitioning, the
tenant-leading composite key rule, UUIDv7 identifiers, soft-delete + purge
lifecycle, the transactional outbox, and the tenant read-model projection —
lives only as prose in `services/AGENTS.md` and as implicit convention in the
`B1.0.0__baseline.sql` files. `openspec/specs/db-schema/spec.md` is empty, so
schema reviews and new tables have no authoritative, testable contract to
reference. Config already instructs authors to read this spec before proposing
schema changes; it needs real content.

## What Changes

- Establish the `db-schema` capability spec as the authoritative behavioral
  contract for every service's Postgres persistence layer.
- Define the database-per-service ownership rule: no cross-service foreign keys;
  correlation IDs (e.g. `meeting_id` in `record`) are plain UUID columns.
- Define multi-tenant hash partitioning: every business table is
  `PARTITION BY HASH (tenant_id)` with 16 partitions and `tenant_id` (Jira
  cloudId) as the leading column of every primary key, unique constraint, and
  foreign key so partition pruning keeps per-tenant work local.
- Define the identifier convention: UUIDv7 (`uuidv7()` / time-ordered) surrogate
  keys, composite `(tenant_id, id)` primary keys, hashed tokens.
- Define the soft-delete + retention lifecycle: `deleted_at` / `deleted_by`,
  `purge_after`, and partial indexes that exclude soft-deleted rows.
- Define the tenant read-model projection: `tenants` as a small, non-partitioned
  table synchronised from the tenant service via Kafka, referenced by business
  tables.
- Define the transactional outbox contract: the `outbox_event` table shape,
  tenant-scoped PK, UUIDv7 id, and the unpublished-scan partial index.
- Define migration governance: Flyway `B1.0.0__baseline.sql` on clean DB,
  incremental `V<n>__<desc>.sql`, `ddl-auto: validate`, and the
  never-edit-applied-migrations rule keeping entities and schema in sync.

## Capabilities

### New Capabilities

- `db-schema`: The cross-service persistence contract — database-per-service
  ownership, multi-tenant hash partitioning, UUIDv7 composite keys, soft-delete
  and retention lifecycle, the tenant projection read model, the transactional
  outbox, and Flyway migration governance that every service's Postgres schema
  must satisfy.

### Modified Capabilities

<!-- None: no existing spec's requirements change. -->

## Impact

- Spec: populates `openspec/specs/db-schema/spec.md` (currently empty).
- Referenced by: `openspec/config.yaml` design rule ("Read
  openspec/specs/db-schema/spec.md before proposing schema changes").
- Documents behavior already implemented by the `B1.0.0__baseline.sql` baselines
  of `tenant`, `meet`, and `record`, their `infrastructure/persistence` JPA
  entities/adapters, and the shared outbox pattern. No code changes; this is a
  documentation/contract artifact.
