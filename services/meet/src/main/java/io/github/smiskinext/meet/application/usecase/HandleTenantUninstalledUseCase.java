package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.HandleTenantUninstalledCommand;

/**
 * Inbound port for processing a tenant-uninstalled event.
 *
 * <p>Upserts the local {@code tenants} projection row with {@code status = UNINSTALLED},
 * recording {@code uninstalledAt} and {@code purgeAfter}.
 */
public interface HandleTenantUninstalledUseCase {

    void handle(HandleTenantUninstalledCommand command);
}
