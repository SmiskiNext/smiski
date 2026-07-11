## 1. Populate the canonical db-schema spec

- [ ] 1.1 Copy the ADDED requirements from
      `openspec/changes/document-db-schema/specs/db-schema/spec.md` into the
      canonical `openspec/specs/db-schema/spec.md` (currently empty)
- [ ] 1.2 Confirm the canonical spec has no delta headers (`ADDED`/`MODIFIED`)
      after archive — it must read as a standalone capability spec
- [ ] 1.3 Run `pnpm lint` / `pnpm format` so the spec passes markdownlint and
      prettier

## 2. Verify requirements against the real baselines

- [x] 2.1 Verify "Database-per-service ownership": confirm `record`'s
      `recordings.meeting_id` is a plain `UUID` with no FK to `meetings`, and
      that no cross-service FK exists in any baseline
- [x] 2.2 Verify "Multi-tenant hash partitioning": confirm every business table
      in `meet` and `record` baselines is `PARTITION BY HASH (tenant_id)` with
      `_p00..._p15` (MODULUS 16), and that `tenants` is unpartitioned
- [x] 2.3 Verify "UUIDv7 identifier and composite key convention": confirm
      business tables default `id` to `uuidv7()` with
      `PRIMARY KEY (tenant_id, id)`, `tenants` is keyed by cloudId, and
      `invite_tokens` stores only `token_hash`
- [x] 2.4 Verify "Soft-delete and retention lifecycle": confirm
      `deleted_at`/`deleted_by`/`purge_after` exist on content tables, live
      indexes use `WHERE deleted_at IS NULL`, and purge indexes use
      `WHERE deleted_at IS NOT NULL` (e.g. `uq_meetings_short_code`,
      `idx_meetings_purge`)
- [x] 2.5 Verify "Tenant read-model projection": confirm `meet` and `record`
      each have a non-partitioned `tenants` projection keyed by `tenant_id`, and
      `meet.meetings` has `fk_meetings_tenant`
- [x] 2.6 Verify "Transactional outbox": confirm each DB-owning service has an
      `outbox_event` table with `PRIMARY KEY (tenant_id, id)`, UUIDv7 `id`,
      `retry_count`/`last_error`, and
      `idx_outbox_event_unpublished ... WHERE published_at IS NULL`
- [x] 2.7 Verify "Flyway migration governance": confirm each DB-owning service
      has a single `B1.0.0__baseline.sql` under `db/migration/` and that
      `application.yaml` sets `ddl-auto: validate`

## 3. Cross-reference and finalize

- [x] 3.1 Confirm `openspec/config.yaml` design rule pointing at
      `openspec/specs/db-schema/spec.md` now resolves to real content
- [x] 3.2 Run `openspec validate document-db-schema --strict` and resolve any
      reported issues
- [ ] 3.3 Archive the change once the canonical spec is in place
