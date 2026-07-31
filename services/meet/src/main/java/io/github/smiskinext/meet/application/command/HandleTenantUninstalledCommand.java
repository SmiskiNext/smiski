package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Command to handle a tenant-uninstalled event received from the tenant service.
 *
 * @param tenantId the Jira cloudId
 * @param cloudId the Jira cloudId
 * @param updatedAt timestamp from the event
 * @param uninstalledAt timestamp when the tenant was uninstalled
 * @param purgeAfter deadline after which tenant data may be purged
 */
public record HandleTenantUninstalledCommand(
        String tenantId,
        String cloudId,
        Instant updatedAt,
        @Nullable Instant uninstalledAt,
        @Nullable Instant purgeAfter)
        implements Command {}
