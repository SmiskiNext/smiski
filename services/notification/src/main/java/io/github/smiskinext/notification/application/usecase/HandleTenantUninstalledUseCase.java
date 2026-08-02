package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.HandleTenantUninstalledCommand;

/**
 * Inbound port for handling a tenant-uninstalled event.
 */
public interface HandleTenantUninstalledUseCase {

    void handle(HandleTenantUninstalledCommand command);
}
