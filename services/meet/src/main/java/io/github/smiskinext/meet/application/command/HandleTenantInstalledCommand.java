package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Command to handle a tenant-installed event received from the tenant service.
 *
 * @param tenantId the Jira cloudId (stable across reinstall)
 * @param cloudId the Jira cloudId
 * @param siteUrl the Atlassian site URL, or {@code null} when blank in the event
 * @param updatedAt timestamp from the event
 */
public record HandleTenantInstalledCommand(
        String tenantId, String cloudId, @Nullable String siteUrl, Instant updatedAt)
        implements Command {}
