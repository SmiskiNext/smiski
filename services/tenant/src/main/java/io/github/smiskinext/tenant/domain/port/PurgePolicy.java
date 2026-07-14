package io.github.smiskinext.tenant.domain.port;

import java.time.Duration;

/**
 * Provides the retention window used to compute the purge deadline when a tenant is uninstalled.
 */
public interface PurgePolicy {

    Duration retention();
}
