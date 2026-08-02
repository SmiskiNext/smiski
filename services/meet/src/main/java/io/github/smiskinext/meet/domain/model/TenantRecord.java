package io.github.smiskinext.meet.domain.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Read-model projection of a tenant as synchronised from the tenant service via Kafka.
 *
 * <p>This is not an aggregate — it carries no domain events. It represents the latest known
 * lifecycle state of a tenant in the local {@code tenants} projection table.
 *
 * @param tenantId the Jira cloudId (stable across reinstall)
 * @param cloudId the Jira cloudId (same as tenantId, kept for explicitness)
 * @param siteUrl the Atlassian site URL, or {@code null} when not provided
 * @param status current lifecycle status
 * @param updatedAt timestamp of the last projection update
 * @param uninstalledAt timestamp when the tenant was uninstalled, or {@code null} if still active
 * @param purgeAfter deadline after which tenant data may be purged, or {@code null} if not set
 */
public record TenantRecord(
        String tenantId,
        String cloudId,
        @Nullable String siteUrl,
        TenantStatus status,
        Instant updatedAt,
        @Nullable Instant uninstalledAt,
        @Nullable Instant purgeAfter) {}
