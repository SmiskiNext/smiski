-- Search/list indexes for the tenant meeting listing endpoint (POST /api/1/meetings).
--
-- Both indexes are partial (WHERE deleted_at IS NULL) to match the always-applied
-- soft-delete exclusion and to stay aligned with the existing keyset/filter indexes.
-- Tenant-wide CREATED_AT sorting already uses idx_meetings_keyset from the baseline.
-- ============================================================================
-- START_TIME sort keyset: COALESCE(start_time, created_at) folds the null start_time
-- of INSTANT meetings into a single comparable ordering value. The expression and its
-- DESC, id DESC direction must match the query's ORDER BY for the index to be used.
CREATE INDEX idx_meetings_start_time_keyset ON meetings (
    tenant_id,
    COALESCE(start_time, created_at) DESC,
    id DESC
)
WHERE
    deleted_at IS NULL;

-- creatorId filter combined with the default CREATED_AT sort.
CREATE INDEX idx_meetings_host_keyset ON meetings (tenant_id, host_id, created_at DESC, id DESC)
WHERE
    deleted_at IS NULL;
