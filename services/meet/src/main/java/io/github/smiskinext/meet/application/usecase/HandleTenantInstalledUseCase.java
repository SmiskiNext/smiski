package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.HandleTenantInstalledCommand;

/**
 * Inbound port for processing a tenant-installed event.
 *
 * <p>Upserts the local {@code tenants} projection row with {@code status = ACTIVE}.
 */
public interface HandleTenantInstalledUseCase {

    void handle(HandleTenantInstalledCommand command);
}
